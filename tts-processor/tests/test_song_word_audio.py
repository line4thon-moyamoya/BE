from array import array
import io
import json
import shutil
import struct
import unittest
from unittest.mock import patch
import wave

from fastapi import HTTPException

import song_router
from song_mixer import SongMixError, _read_word_metadata, compose_word, mix_song
from test_song_mixer import wav_bytes
from test_song_router import upload


@unittest.skipUnless(shutil.which("ffmpeg"), "FFmpeg is required for audio integration tests")
class SongWordAudioTest(unittest.TestCase):
    def samples(self, audio):
        with wave.open(io.BytesIO(audio), "rb") as wav:
            return array("h", wav.readframes(wav.getnframes()))

    def test_cached_word_preserves_original_beats_gain_and_backing_ducking(self):
        voices = [wav_bytes(0.4, frequency=440), wav_bytes(0.35, frequency=660)]
        for offsets in ([0, 0.99], [0, 0.5, 0.99, 1.49], [0, 0.5, 0.99],
                        [0, 0.25, 0.5, 0.74, 0.99], [0]):
            with self.subTest(offsets=offsets):
                plan = [(i % 2, start, round((offsets[i + 1] if i + 1 < len(offsets) else 4) - start, 3))
                        for i, start in enumerate(offsets)]
                backing = wav_bytes(6, amplitude=0.12, frequency=220)
                direct = mix_song(backing, voices, [(i, start + 1, available) for i, start, available in plan])
                clip = compose_word(voices, plan)
                reused = mix_song(backing, [clip], [(0, 1, None)], prepared_words=True)
                first, second = self.samples(direct), self.samples(reused)
                self.assertEqual(len(first), len(second))
                rms_error = (sum((a - b) ** 2 for a, b in zip(first, second)) / len(first)) ** 0.5
                self.assertLess(rms_error, 2)

    def test_quiet_consonant_before_first_beat_is_preserved(self):
        output = io.BytesIO()
        with wave.open(output, "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(24000)
            for part in [wav_bytes(0.2, amplitude=0.02), wav_bytes(0.2)]:
                with wave.open(io.BytesIO(part), "rb") as source:
                    wav.writeframes(source.readframes(source.getnframes()))
        voice = output.getvalue()
        backing = wav_bytes(2, amplitude=0)
        clip = compose_word([voice], [(0, 0, None)])
        self.assertGreater(_read_word_metadata(clip)["lead"], 0.1)
        reused = self.samples(mix_song(backing, [clip], [(0, 0.5, None)], prepared_words=True))[::2]
        direct = self.samples(mix_song(backing, [voice], [(0, 0.5, None)]))[::2]
        self.assertGreater(max(abs(x) for x in reused[round(0.35 * 44100):round(0.45 * 44100)]), 100)
        strong_start = next(i / 44100 for i, x in enumerate(reused) if abs(x) > 4000)
        self.assertAlmostEqual(strong_start, 0.5, delta=0.02)
        self.assertLess(sum(abs(a - b) for a, b in zip(reused, direct)) / len(direct), 2)

    def test_five_word_slots_and_overlapping_tails_match_original_mix(self):
        voices = [wav_bytes(0.8, frequency=440), wav_bytes(3.4, frequency=660)]
        plan = [(0, 0, 0.5), (1, 0.5, 0.49), (0, 0.99, 0.5), (1, 1.49, 2.51)]
        backing = wav_bytes(33, amplitude=0.08, frequency=220)
        starts = [12, 16, 20, 24, 28]
        direct = self.samples(mix_song(backing, voices,
                                      [(i, base + offset, available)
                                       for base in starts for i, offset, available in plan]))
        clip = compose_word(voices, plan)
        reused = self.samples(mix_song(backing, [clip], [(0, start, None) for start in starts], prepared_words=True))
        self.assertEqual(len(direct), len(reused))
        rms_error = (sum((a - b) ** 2 for a, b in zip(direct, reused)) / len(direct)) ** 0.5
        self.assertLess(rms_error, 2)

    def test_silent_syllable_does_not_shift_other_syllables_after_caching(self):
        voices = [wav_bytes(amplitude=0), wav_bytes()]
        clip = compose_word(voices, [(0, 0, 0.5), (1, 0.5, 3.5)])
        result = self.samples(mix_song(wav_bytes(3, amplitude=0), [clip], [(0, 1, None)], prepared_words=True))[::2]
        self.assertLess(max(abs(x) for x in result[:round(1.49 * 44100)]), 100)
        self.assertGreater(max(abs(x) for x in result[round(1.51 * 44100):round(1.65 * 44100)]), 1000)

    def test_all_silent_word_does_not_extend_or_duck_backing(self):
        backing = wav_bytes(1)
        clip = compose_word([wav_bytes(amplitude=0)], [(0, 0, 4)])
        reused = mix_song(backing, [clip], [(0, 5, None)], prepared_words=True)
        direct = mix_song(backing, [wav_bytes(amplitude=0)], [(0, 5)])
        self.assertEqual(self.samples(reused), self.samples(direct))

    def test_reusing_whole_long_word_keeps_tail_after_backing(self):
        backing = wav_bytes(1, amplitude=0)
        voice = wav_bytes(1.5)
        clip = compose_word([voice], [(0, 0, None)])
        reused = self.samples(mix_song(backing, [clip], [(0, 0.8, None)], prepared_words=True))
        direct = self.samples(mix_song(backing, [voice], [(0, 0.8, None)]))
        self.assertEqual(len(reused), len(direct))
        self.assertLess(sum(abs(a - b) for a, b in zip(reused, direct)) / len(direct), 2)

    def test_prepared_mode_rejects_audio_without_metadata(self):
        with self.assertRaises(SongMixError):
            mix_song(wav_bytes(1), [wav_bytes()], [(0, 0)], prepared_words=True)


class SongWordRouterTest(unittest.IsolatedAsyncioTestCase):
    async def test_word_route_receives_only_syllables_and_local_timings(self):
        wav = wav_bytes()
        metadata = '{"placements":[{"audioIndex":0,"startSec":0,"availableSec":4}]}'
        with patch("song_router.compose_word", return_value=wav) as composer:
            response = await song_router.compose_word_audio([upload(wav)], metadata)
        self.assertEqual(response.body, wav)
        composer.assert_called_once_with([wav], [(0, 0, 4)])

    async def test_prepared_words_skip_raw_syllable_path(self):
        wav = wav_bytes()
        metadata = '{"preparedWords":true,"placements":[{"audioIndex":0,"startSec":12}]}'
        with patch("song_router.mix_song", return_value=wav) as mixer:
            await song_router.mix_song_audio(upload(b"backing"), [upload(wav)], metadata)
        mixer.assert_called_once_with(b"backing", [wav], [(0, 12, None)], prepared_words=True)

    async def test_word_route_rejects_prepared_input_and_global_timings(self):
        for metadata in ('{"preparedWords":true,"placements":[{"audioIndex":0,"startSec":0}]}',
                         '{"placements":[{"audioIndex":0,"startSec":12}]}'):
            with self.subTest(metadata=metadata), patch("song_router.compose_word") as composer:
                with self.assertRaises(HTTPException):
                    await song_router.compose_word_audio([upload(wav_bytes())], metadata)
                composer.assert_not_called()

    def test_rejects_corrupt_nonfinite_and_out_of_bounds_metadata(self):
        for value in ({"version": 1, "lead": float("nan"), "ranges": []},
                      {"version": 1, "lead": 0, "ranges": [[0, float("inf")]]},
                      {"version": 1, "lead": 0, "ranges": [[1, 0]]},
                      {"version": 2, "lead": 0, "ranges": []}):
            payload = json.dumps(value).encode()
            audio = b"RIFF" + b"\x00" * 4 + b"WAVEmoya" + struct.pack("<I", len(payload)) + payload
            with self.assertRaises(SongMixError):
                _read_word_metadata(audio)

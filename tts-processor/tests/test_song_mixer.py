from array import array
import io
import math
from pathlib import Path
import shutil
import subprocess
import unittest
from unittest.mock import patch
import wave

from song_mixer import SongMixError, _prepare_voice, mix_song


def wav_bytes(duration=0.2, rate=24000, amplitude=0.25, frequency=440):
    samples = array("h", (round(amplitude * 32767 * math.sin(2 * math.pi * frequency * i / rate))
                          for i in range(round(rate * duration))))
    output = io.BytesIO()
    with wave.open(output, "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(rate)
        wav.writeframes(samples.tobytes())
    return output.getvalue()


class SongMixerTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("ffmpeg"), "FFmpeg is required for audio integration tests")
    def test_aligns_strong_speech_to_beat_while_preserving_quiet_consonant(self):
        output = io.BytesIO()
        with wave.open(output, "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(24000)
            for part in [wav_bytes(0.2, amplitude=0.02), wav_bytes(0.2)]:
                with wave.open(io.BytesIO(part), "rb") as source:
                    wav.writeframes(source.readframes(source.getnframes()))
        result = mix_song(wav_bytes(1, amplitude=0), [output.getvalue()], [(0, 0.5, None)])
        with wave.open(io.BytesIO(result), "rb") as wav:
            samples = array("h", wav.readframes(wav.getnframes()))[::2]
        strong_start = next(i / 44100 for i, x in enumerate(samples) if abs(x) > 4000)
        self.assertAlmostEqual(strong_start, 0.5, delta=0.02)
        self.assertGreater(max(abs(x) for x in samples[round(0.35 * 44100):round(0.45 * 44100)]), 100)

    @unittest.skipUnless(shutil.which("ffmpeg"), "FFmpeg is required for audio integration tests")
    def test_shortens_long_syllable_to_available_interval_without_changing_pitch(self):
        result = mix_song(wav_bytes(1, amplitude=0), [wav_bytes(0.4)], [(0, 0.5, 0.3)])
        with wave.open(io.BytesIO(result), "rb") as wav:
            samples = array("h", wav.readframes(wav.getnframes()))[::2]
        self.assertLess(max(abs(x) for x in samples[round(0.82 * 44100):]), 100)
        segment = samples[round(0.54 * 44100):round(0.64 * 44100)]
        crossings = sum(a <= 0 < b for a, b in zip(segment, segment[1:]))
        self.assertAlmostEqual(crossings / 0.1, 440, delta=20)

    def test_trims_outer_silence_without_removing_internal_pause(self):
        parts = [wav_bytes(0.45, amplitude=0), wav_bytes(0.15),
                 wav_bytes(0.1, amplitude=0), wav_bytes(0.15), wav_bytes(0.4, amplitude=0)]
        output = io.BytesIO()
        with wave.open(output, "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(24000)
            for part in parts:
                with wave.open(io.BytesIO(part), "rb") as source:
                    wav.writeframes(source.readframes(source.getnframes()))
        prepared, start, end = _prepare_voice(output.getvalue())
        with wave.open(io.BytesIO(prepared), "rb") as wav:
            duration = wav.getnframes() / wav.getframerate()
            samples = array("h", wav.readframes(wav.getnframes()))
        self.assertLess(start, 0.04)
        self.assertGreater(end, 0.39)
        self.assertGreater(duration, 0.4)
        self.assertLess(duration, 0.48)
        self.assertEqual(max(abs(x) for x in samples[round(0.2 * 24000):round(0.24 * 24000)]), 0)

    def test_silent_tts_fails_with_voice_index_instead_of_omitting_word(self):
        with self.assertRaisesRegex(SongMixError, "인덱스 0"):
            mix_song(wav_bytes(1), [wav_bytes(amplitude=0)], [(0, 0.5)])

    @unittest.skipUnless(shutil.which("ffmpeg"), "FFmpeg is required for audio integration tests")
    def test_padded_voice_starts_at_requested_beat(self):
        output = io.BytesIO()
        with wave.open(output, "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(24000)
            for part in [wav_bytes(0.45, amplitude=0), wav_bytes(0.2), wav_bytes(0.3, amplitude=0)]:
                with wave.open(io.BytesIO(part), "rb") as source:
                    wav.writeframes(source.readframes(source.getnframes()))
        result = mix_song(wav_bytes(2, amplitude=0), [output.getvalue()], [(0, 0.5), (0, 1)])
        with wave.open(io.BytesIO(result), "rb") as wav:
            samples = array("h", wav.readframes(wav.getnframes()))[::2]
        for beat in [0.5, 1]:
            onset = next(i / 44100 for i in range(round(beat * 44100), len(samples))
                         if abs(samples[i]) > 1000)
            self.assertLess(onset - beat, 0.04)
        self.assertLess(max(abs(x) for x in samples[round(0.8 * 44100):round(0.99 * 44100)]), 100)

    @unittest.skipUnless(shutil.which("ffmpeg"), "FFmpeg is required for audio integration tests")
    def test_balances_quiet_and_loud_voices(self):
        result = mix_song(wav_bytes(2, amplitude=0),
                          [wav_bytes(amplitude=0.08), wav_bytes(amplitude=0.5)],
                          [(0, 0.5), (1, 1.0)])
        with wave.open(io.BytesIO(result), "rb") as wav:
            samples = array("h", wav.readframes(wav.getnframes()))
        def rms(start, end):
            segment = samples[round(start * 88200):round(end * 88200)]
            return math.sqrt(sum(x * x for x in segment) / len(segment)) / 32768
        self.assertAlmostEqual(rms(0.55, 0.65), rms(1.05, 1.15), delta=0.005)
        self.assertAlmostEqual(rms(0.55, 0.65), 10 ** (-18 / 20), delta=0.01)

    @unittest.skipUnless(shutil.which("ffmpeg"), "FFmpeg is required for audio integration tests")
    def test_reduces_backing_during_voice_and_restores_it_afterwards(self):
        result = mix_song(wav_bytes(2, amplitude=0.15),
                          [wav_bytes(0.4, frequency=880)], [(0, 0.5)])
        with wave.open(io.BytesIO(result), "rb") as wav:
            samples = array("h", wav.readframes(wav.getnframes()))[::2]
        def backing_amplitude(start, end):
            first, last = round(start * 44100), round(end * 44100)
            return 2 * sum(samples[i] / 32768 * math.sin(2 * math.pi * 440 * i / 44100)
                           for i in range(first, last)) / (last - first)
        before = backing_amplitude(0.2, 0.3)
        during = backing_amplitude(0.6, 0.7)
        after = backing_amplitude(1.3, 1.4)
        self.assertAlmostEqual(20 * math.log10(during / before), -2, delta=0.2)
        self.assertAlmostEqual(after, before, delta=0.002)
        self.assertAlmostEqual(before, 0.15 / math.sqrt(2) * 10 ** (-1.6 / 20), delta=0.002)

    @unittest.skipUnless(shutil.which("ffmpeg"), "FFmpeg is required for audio integration tests")
    def test_places_reused_voice_at_requested_times_and_resamples(self):
        result = mix_song(wav_bytes(2, amplitude=0), [wav_bytes()], [(0, 0.5), (0, 1.0)])
        with wave.open(io.BytesIO(result), "rb") as wav:
            self.assertEqual(wav.getframerate(), 44100)
            self.assertEqual(wav.getnchannels(), 2)
            self.assertEqual(wav.getsampwidth(), 2)
            samples = array("h", wav.readframes(wav.getnframes()))
            self.assertAlmostEqual(wav.getnframes() / 44100, 2, places=2)

        def rms(start, end):
            segment = samples[round(start * 88200):round(end * 88200)]
            return math.sqrt(sum(x * x for x in segment) / len(segment)) / 32768

        self.assertLess(rms(0.3, 0.49), 0.001)
        self.assertGreater(rms(0.51, 0.65), 0.05)
        self.assertLess(rms(0.8, 0.99), 0.001)
        self.assertGreater(rms(1.01, 1.15), 0.05)

    @unittest.skipUnless(shutil.which("ffmpeg"), "FFmpeg is required for audio integration tests")
    def test_does_not_cut_voice_when_it_extends_past_backing(self):
        result = mix_song(wav_bytes(1, amplitude=0), [wav_bytes(0.4)], [(0, 0.8)])
        with wave.open(io.BytesIO(result), "rb") as wav:
            self.assertAlmostEqual(wav.getnframes() / wav.getframerate(), 1.2, places=2)

    @unittest.skipUnless(shutil.which("ffmpeg"), "FFmpeg is required for audio integration tests")
    def test_rejects_undecodable_audio(self):
        with self.assertRaises(SongMixError):
            mix_song(b"invalid backing", [wav_bytes()], [(0, 0)])

    def test_missing_ffmpeg_cleans_temporary_files(self):
        paths = []

        def missing(command, **kwargs):
            paths.extend([Path(command[command.index("-i") + 1]), Path(command[-1])])
            raise FileNotFoundError()

        with patch("song_mixer.subprocess.run", side_effect=missing):
            with self.assertRaisesRegex(SongMixError, "FFmpeg"):
                mix_song(b"backing", [wav_bytes()], [(0, 0)])
        self.assertTrue(paths)
        self.assertTrue(all(not path.parent.exists() for path in paths))

    def test_timeout_is_translated(self):
        with patch("song_mixer.subprocess.run", side_effect=subprocess.TimeoutExpired("ffmpeg", 60)):
            with self.assertRaisesRegex(SongMixError, "초과"):
                mix_song(b"backing", [wav_bytes()], [(0, 0)])

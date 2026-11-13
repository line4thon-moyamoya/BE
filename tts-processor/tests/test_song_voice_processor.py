import io
import unittest
import wave
from unittest.mock import patch

import parselmouth
import numpy as np
from fastapi import HTTPException
from starlette.datastructures import UploadFile

import song_router
from song_voice_processor import process_song_voice
from test_song_mixer import wav_bytes


class SongVoiceProcessorTest(unittest.TestCase):
    def test_preserves_duration_and_raises_original_pitch_only_slightly(self):
        source = wav_bytes(duration=0.5, frequency=220)
        output = process_song_voice(source)
        with wave.open(io.BytesIO(source)) as wav:
            original = parselmouth.Sound(np.frombuffer(wav.readframes(wav.getnframes()), dtype="<i2")
                                         .astype(float) / 32768, wav.getframerate())
        with wave.open(io.BytesIO(output)) as wav:
            result = parselmouth.Sound(np.frombuffer(wav.readframes(wav.getnframes()), dtype="<i2")
                                       .astype(float) / 32768, wav.getframerate())
        self.assertAlmostEqual(result.duration, original.duration, delta=0.02)
        pitch = result.to_pitch().selected_array["frequency"]
        voiced = pitch[pitch > 0]
        self.assertTrue(len(voiced))
        self.assertAlmostEqual(float(voiced.mean()), 231, delta=8)
        self.assertLessEqual(float(abs(result.values).max()), 0.951)

    def test_silent_voice_returns_original_for_java_quality_check(self):
        source = wav_bytes(duration=0.3, amplitude=0)
        self.assertEqual(process_song_voice(source), source)


class SongVoiceRouteTest(unittest.IsolatedAsyncioTestCase):
    async def test_song_route_uses_dedicated_processor(self):
        source = wav_bytes()
        with patch("song_router.process_song_voice", return_value=source) as processor:
            result = await song_router.process_song_audio(UploadFile(io.BytesIO(source)))
        processor.assert_called_once_with(source)
        self.assertEqual(result.body, source)

    async def test_invalid_wav_is_rejected_before_processing(self):
        with patch("song_router.process_song_voice") as processor:
            with self.assertRaises(HTTPException) as error:
                await song_router.process_song_audio(UploadFile(io.BytesIO(b"invalid")))
        self.assertEqual(error.exception.status_code, 400)
        processor.assert_not_called()

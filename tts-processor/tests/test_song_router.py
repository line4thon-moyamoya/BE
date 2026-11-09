import io
import json
import unittest
from unittest.mock import patch

from fastapi import HTTPException, UploadFile
from starlette.datastructures import Headers

import main
import song_router
from song_mixer import SongMixError
from test_song_mixer import wav_bytes


def upload(data, content_type="audio/wav"):
    return UploadFile(io.BytesIO(data), filename="audio", headers=Headers({"content-type": content_type}))


class SongRouterTest(unittest.IsolatedAsyncioTestCase):
    async def test_multipart_request_reaches_separate_router(self):
        boundary = b"moya-test-boundary"
        wav = wav_bytes()
        body = b""
        for name, filename, content_type, data in [
            ("backing", "backing.m4a", "application/octet-stream", b"backing"),
            ("voices", "voice-0.wav", "audio/wav", wav),
            ("metadata", None, "text/plain", b'{"placements":[{"audioIndex":0,"startSec":12}]}'),
        ]:
            disposition = f'Content-Disposition: form-data; name="{name}"'
            if filename:
                disposition += f'; filename="{filename}"'
            body += (b"--" + boundary + b"\r\n" + disposition.encode() + b"\r\nContent-Type: "
                     + content_type.encode() + b"\r\n\r\n" + data + b"\r\n")
        body += b"--" + boundary + b"--\r\n"
        messages = []

        async def receive():
            return {"type": "http.request", "body": body, "more_body": False}

        async def send(message):
            messages.append(message)

        scope = {"type": "http", "asgi": {"version": "3.0"}, "http_version": "1.1",
                 "method": "POST", "scheme": "http", "path": "/songs/mix", "query_string": b"",
                 "headers": [(b"content-type", b"multipart/form-data; boundary=" + boundary),
                             (b"content-length", str(len(body)).encode())]}
        with patch("song_router.mix_song", return_value=wav) as mixer:
            await main.app(scope, receive, send)
        self.assertEqual(messages[0]["status"], 200)
        self.assertEqual(b"".join(m.get("body", b"") for m in messages), wav)
        mixer.assert_called_once_with(b"backing", [wav], [(0, 12, None)])

    async def test_returns_wav_and_passes_index_and_timing(self):
        backing = b"backing"
        voice = wav_bytes()
        plan = {"placements": [{"audioIndex": 0, "startSec": 12}, {"audioIndex": 0, "startSec": 12.99}]}
        with patch("song_router.mix_song", return_value=voice) as mixer:
            response = await song_router.mix_song_audio(upload(backing), [upload(voice)], json.dumps(plan))
        self.assertEqual(response.media_type, "audio/wav")
        self.assertEqual(response.body, voice)
        mixer.assert_called_once_with(backing, [voice], [(0, 12, None), (0, 12.99, None)])

    async def test_invalid_metadata_does_not_start_mixer(self):
        invalid_plans = ["not-json", '{"placements":[]}',
                         '{"placements":[{"audioIndex":-1,"startSec":0}]}',
                         '{"placements":[{"audioIndex":1,"startSec":0}]}',
                         '{"placements":[{"audioIndex":0,"startSec":-1}]}',
                         '{"placements":[{"audioIndex":0,"startSec":NaN}]}',
                         json.dumps({"placements": [{"audioIndex": 0, "startSec": 0}] * 26})]
        with patch("song_router.mix_song") as mixer:
            for metadata in invalid_plans:
                with self.subTest(metadata=metadata), self.assertRaises(HTTPException) as error:
                    await song_router.mix_song_audio(upload(b"backing"), [upload(wav_bytes())], metadata)
                self.assertEqual(error.exception.status_code, 400)
            mixer.assert_not_called()

    async def test_invalid_files_do_not_start_mixer(self):
        plan = '{"placements":[{"audioIndex":0,"startSec":12}]}'
        for backing, voice in [(b"", wav_bytes()), (b"backing", b""), (b"backing", b"not-wav")]:
            with self.subTest(backing=bool(backing), voice=bool(voice)), patch("song_router.mix_song") as mixer:
                with self.assertRaises(HTTPException) as error:
                    await song_router.mix_song_audio(upload(backing), [upload(voice)], plan)
                self.assertEqual(error.exception.status_code, 400)
                mixer.assert_not_called()

    async def test_file_size_limit(self):
        with self.assertRaises(HTTPException) as error:
            await song_router.read_audio(upload(b"12345"), 4)
        self.assertEqual(error.exception.status_code, 400)

    async def test_mix_failure_returns_422(self):
        with patch("song_router.mix_song", side_effect=SongMixError("mix failed")):
            with self.assertRaises(HTTPException) as error:
                await song_router.mix_song_audio(upload(b"backing"), [upload(wav_bytes())],
                                                 '{"placements":[{"audioIndex":0,"startSec":12}]}')
        self.assertEqual(error.exception.status_code, 422)

    async def test_existing_process_route_still_returns_processed_wav(self):
        wav = wav_bytes()
        with patch("main.process_audio", return_value=wav) as processor:
            response = await main.process_tts(upload(wav))
        self.assertEqual(response.body, wav)
        self.assertEqual(response.media_type, "audio/wav")
        processor.assert_called_once_with(wav)
        self.assertTrue({"/process", "/songs/mix"}.issubset(main.app.openapi()["paths"]))

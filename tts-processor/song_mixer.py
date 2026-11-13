import os
from array import array
import io
import json
import struct
import math
from pathlib import Path
import subprocess
import sys
import tempfile
import wave


class SongMixError(Exception):
    pass


def _prepare_voice(audio: bytes) -> tuple[bytes, float, float]:
    with wave.open(io.BytesIO(audio), "rb") as wav:
        if wav.getsampwidth() != 2:
            raise SongMixError("음성은 PCM 16비트 WAV여야 합니다.")
        params = wav.getparams()
        samples = array("h", wav.readframes(wav.getnframes()))
    if sys.byteorder != "little":
        samples.byteswap()
    frame_size = max(1, round(params.framerate * 0.02)) * params.nchannels
    levels = [math.sqrt(sum(x * x for x in samples[i:i + frame_size])
                        / len(samples[i:i + frame_size]))
              for i in range(0, len(samples), frame_size)]
    peak = max(levels, default=0)
    active = [i for i, level in enumerate(levels) if level > max(32, peak * 0.1)]
    if not active:
        return audio, 0, 0
    # Keep quiet consonants and a small margin at both ends of the utterance.
    boundaries = [i for i, level in enumerate(levels) if level > max(16, peak * 0.02)]
    start_frame = max(0, boundaries[0] * (frame_size // params.nchannels)
                      - round(params.framerate * 0.01))
    end_frame = min(params.nframes, (boundaries[-1] + 1) * (frame_size // params.nchannels)
                    + round(params.framerate * 0.03))
    rms = math.sqrt(sum(levels[i] ** 2 for i in active) / len(active))
    # Mono-to-stereo conversion reduces each channel by 3 dB.
    target_db = -15 if params.nchannels == 1 else -18
    gain = min(10 ** (12 / 20), 32768 * 10 ** (target_db / 20) / rms,
               32767 * 10 ** (-3 / 20) / max(abs(x) for x in samples))
    trimmed = samples[start_frame * params.nchannels:end_frame * params.nchannels]
    scaled = array("h", (round(x * gain) for x in trimmed))
    if sys.byteorder != "little":
        scaled.byteswap()
    output = io.BytesIO()
    with wave.open(output, "wb") as wav:
        wav.setparams(params)
        wav.writeframes(scaled.tobytes())
    anchors = [i for i, level in enumerate(levels) if level > max(32, peak * 0.35)]
    anchor = next((i for i in anchors if i + 1 in anchors), anchors[0])
    offset = start_frame / params.framerate
    return output.getvalue(), max(0, anchor * 0.02 - offset), min(
        (active[-1] + 1) * 0.02 - offset, (end_frame - start_frame) / params.framerate)


def mix_song(backing: bytes, voices: list[bytes],
             placements: list[tuple[int, float] | tuple[int, float, float | None]],
             *, prepared_words: bool = False, word_clip: bool = False) -> bytes:
    with tempfile.TemporaryDirectory(prefix="moya-song-") as directory:
        root = Path(directory)
        backing_path = root / "backing.audio"
        backing_path.write_bytes(backing)
        voice_paths = []
        active_ranges = []
        word_metadata = []
        for index, voice in enumerate(voices):
            try:
                if prepared_words:
                    word_metadata.append(_read_word_metadata(voice))
                    active_start, active_end = 0, 0
                else:
                    voice, active_start, active_end = _prepare_voice(voice)
            except (wave.Error, EOFError) as exc:
                raise SongMixError("음성 WAV 파일을 읽을 수 없습니다.") from exc
            except SongMixError as exc:
                raise SongMixError(f"음성 인덱스 {index}: {exc}") from exc
            path = root / f"voice-{index}.wav"
            path.write_bytes(voice)
            voice_paths.append(path)
            active_ranges.append((active_start, active_end))

        output_path = root / "song.wav"
        command = [os.environ.get("FFMPEG_PATH", "ffmpeg"), "-nostdin", "-hide_banner",
                   "-loglevel", "error", "-y", "-i", str(backing_path)]
        lead = 0
        if word_clip:
            for placement in placements:
                audio_index, offset = placement[:2]
                available = placement[2] if len(placement) == 3 else None
                active_start, active_end = active_ranges[audio_index]
                rate = _tempo(active_start, active_end, available)
                lead = max(lead, active_start / rate - offset)
            lead = math.ceil(lead * 1000) / 1000
        envelopes = []
        speech_ranges = []
        timings = []
        for placement in placements:
            audio_index, start_sec = placement[:2]
            available = placement[2] if len(placement) == 3 else None
            if prepared_words:
                metadata = word_metadata[audio_index]
                delay = start_sec - metadata["lead"]
                if delay < 0:
                    raise SongMixError("단어 음원의 시작 시점이 노래 범위를 벗어났습니다.")
                if metadata["ranges"]:
                    timings.append((audio_index, delay, 1))
                for first, last in metadata["ranges"]:
                    speech_ranges.append((start_sec + first, start_sec + last))
                continue
            start_sec += lead
            active_start, active_end = active_ranges[audio_index]
            if active_end <= active_start:
                continue
            rate = _tempo(active_start, active_end, available)
            # Start the quiet consonant before the beat instead of cutting it off.
            delay = max(0, start_sec - active_start / rate)
            timings.append((audio_index, delay, rate))
            start = delay + active_start / rate
            end = delay + active_end / rate
            speech_ranges.append((start, end))
        for start, end in speech_ranges:
            envelopes.append(
                f"max(0,min(1,min((t-{start - 0.04:.6f})/0.04,"
                f"({end + 0.18:.6f}-t)/0.18)))")
        envelope = "min(1," + "+".join(envelopes) + ")" if envelopes else "0"
        filters = [
            "[0:a]aresample=44100,aformat=sample_fmts=fltp:channel_layouts=stereo,"
            f"volume='pow(10,(-1.6-2*({envelope}))/20)':eval=frame[backing]"
        ]
        labels = ["[backing]"]
        for index, (audio_index, delay, rate) in enumerate(timings):
            command.extend(["-i", str(voice_paths[audio_index])])
            delay_ms = round(delay * 1000)
            label = f"clip{index}"
            tempo_filter = "" if prepared_words else f"atempo={rate:.6f},"
            filters.append(
                f"[{index + 1}:a]aresample=44100,aformat=sample_fmts=fltp:channel_layouts=stereo,"
                + tempo_filter + f"adelay=delays={delay_ms}:all=1[{label}]"
            )
            labels.append(f"[{label}]")
        filters.append(
            "".join(labels)
            + f"amix=inputs={len(labels)}:duration=longest:normalize=0,"
              + ("anull[out]" if word_clip else "alimiter=limit=0.95:level=0:latency=1[out]")
        )
        command.extend(["-filter_complex_threads", "1", "-filter_complex", ";".join(filters),
                        "-map", "[out]", "-ar", "44100", "-ac", "2", "-c:a", "pcm_f32le" if word_clip else "pcm_s16le",
                        str(output_path)])
        try:
            subprocess.run(command, check=True, capture_output=True, timeout=60)
        except FileNotFoundError as exc:
            raise SongMixError("FFmpeg를 찾을 수 없습니다.") from exc
        except subprocess.TimeoutExpired as exc:
            raise SongMixError("노래 합성 시간이 초과되었습니다.") from exc
        except subprocess.CalledProcessError as exc:
            raise SongMixError("반주 또는 음성 파일을 합성할 수 없습니다.") from exc
        result = output_path.read_bytes()
        if word_clip:
            metadata = json.dumps({"version": 1, "lead": lead,
                                   "ranges": [(start - lead, end - lead) for start, end in speech_ranges]},
                                  separators=(",", ":")).encode()
            result += b"moya" + struct.pack("<I", len(metadata)) + metadata + b"\x00" * (len(metadata) % 2)
            result = result[:4] + struct.pack("<I", len(result) - 8) + result[8:]
        return result


def _tempo(start: float, end: float, available: float | None) -> float:
    return min(1.6, max(1, (end - start) / max(0.05, available - 0.04))) if available is not None else 1


def compose_word(voices: list[bytes], placements: list[tuple[int, float, float | None]]) -> bytes:
    output = io.BytesIO()
    with wave.open(output, "wb") as wav:
        wav.setnchannels(2)
        wav.setsampwidth(2)
        wav.setframerate(44100)
        wav.writeframes(b"\x00" * 4)
    return mix_song(output.getvalue(), voices, placements, word_clip=True)


def _read_word_metadata(audio: bytes) -> dict:
    if audio[:4] != b"RIFF" or audio[8:12] != b"WAVE":
        raise SongMixError("단어 노래 음원의 WAV 형식이 올바르지 않습니다.")
    offset = 12
    while offset + 8 <= len(audio):
        tag = audio[offset:offset + 4]
        size = struct.unpack_from("<I", audio, offset + 4)[0]
        first = offset + 8
        if first + size > len(audio):
            break
        if tag == b"moya" and size <= 16 * 1024:
            try:
                metadata = json.loads(audio[first:first + size])
                lead, ranges = metadata["lead"], metadata["ranges"]
                if (metadata["version"] != 1 or not isinstance(lead, (int, float))
                        or not math.isfinite(lead) or not 0 <= lead <= 120
                        or not isinstance(ranges, list) or len(ranges) > 25):
                    raise ValueError()
                for start, end in ranges:
                    if (not all(isinstance(value, (int, float)) and math.isfinite(value)
                                for value in (start, end)) or not -120 <= start < end <= 120):
                        raise ValueError()
                return metadata
            except (ValueError, TypeError, KeyError, IndexError) as exc:
                raise SongMixError("단어 노래 음원의 배치 정보가 올바르지 않습니다.") from exc
        offset = first + size + size % 2
    raise SongMixError("단어 노래 음원의 배치 정보가 없습니다.")

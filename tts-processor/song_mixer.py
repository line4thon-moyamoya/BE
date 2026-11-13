import os
from array import array
import io
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
        raise SongMixError("발화가 감지되지 않은 음성입니다. 개별 단어 TTS를 확인해 주세요.")
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
             placements: list[tuple[int, float] | tuple[int, float, float | None]]) -> bytes:
    with tempfile.TemporaryDirectory(prefix="moya-song-") as directory:
        root = Path(directory)
        backing_path = root / "backing.audio"
        backing_path.write_bytes(backing)
        voice_paths = []
        active_ranges = []
        for index, voice in enumerate(voices):
            try:
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
        envelopes = []
        timings = []
        for placement in placements:
            audio_index, start_sec = placement[:2]
            available = placement[2] if len(placement) == 3 else None
            active_start, active_end = active_ranges[audio_index]
            rate = min(1.6, max(1, (active_end - active_start) / max(0.05, available - 0.04))) \
                if available is not None else 1
            # Start the quiet consonant before the beat instead of cutting it off.
            delay = max(0, start_sec - active_start / rate)
            timings.append((audio_index, delay, rate))
            if active_end > active_start:
                start = delay + active_start / rate
                end = delay + active_end / rate
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
            filters.append(
                f"[{index + 1}:a]aresample=44100,aformat=sample_fmts=fltp:channel_layouts=stereo,"
                f"atempo={rate:.6f},"
                f"adelay=delays={delay_ms}:all=1[{label}]"
            )
            labels.append(f"[{label}]")
        filters.append(
            "".join(labels)
            + f"amix=inputs={len(labels)}:duration=longest:normalize=0,"
              "alimiter=limit=0.95:level=0:latency=1[out]"
        )
        command.extend(["-filter_complex_threads", "1", "-filter_complex", ";".join(filters),
                        "-map", "[out]", "-ar", "44100", "-ac", "2", "-c:a", "pcm_s16le",
                        str(output_path)])
        try:
            subprocess.run(command, check=True, capture_output=True, timeout=60)
        except FileNotFoundError as exc:
            raise SongMixError("FFmpeg를 찾을 수 없습니다.") from exc
        except subprocess.TimeoutExpired as exc:
            raise SongMixError("노래 합성 시간이 초과되었습니다.") from exc
        except subprocess.CalledProcessError as exc:
            raise SongMixError("반주 또는 음성 파일을 합성할 수 없습니다.") from exc
        return output_path.read_bytes()

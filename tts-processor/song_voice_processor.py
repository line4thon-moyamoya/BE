from pathlib import Path
from statistics import median
import tempfile

import parselmouth
from parselmouth.praat import call


def process_song_voice(audio: bytes) -> bytes:
    with tempfile.TemporaryDirectory(prefix="moya-song-voice-") as directory:
        source = Path(directory) / "input.wav"
        target = Path(directory) / "output.wav"
        source.write_bytes(audio)
        sound = parselmouth.Sound(str(source))
        pitch = sound.to_pitch(pitch_floor=75, pitch_ceiling=600)
        frequencies = pitch.selected_array["frequency"]
        voiced = frequencies[frequencies > 0]
        if not len(voiced):
            return audio
        processed = call(sound, "Change gender", 75, 600, 1.05,
                         float(median(voiced)) * 1.05, 1.0, 1.0)
        peak = float(call(processed, "Get absolute extremum", 0, 0, "Parabolic"))
        if peak > 0.95:
            processed.scale_peak(0.95)
        processed.save(str(target), "WAV")
        return target.read_bytes()

import os
import tempfile

import parselmouth
from parselmouth.praat import call


def process_audio(audio_bytes: bytes) -> bytes:
    input_path = None
    output_path = None

    try:
        # 입력 WAV를 임시 파일로 저장
        with tempfile.NamedTemporaryFile(
                suffix=".wav", delete=False
        ) as input_file:
            input_file.write(audio_bytes)
            input_path = input_file.name

        # Praat Sound 객체 생성
        sound = parselmouth.Sound(input_path)

        # 피치 및 포먼트 조정
        processed = call(
            sound,
            "Change gender",
            75,     # Pitch floor (Hz)
            600,    # Pitch ceiling (Hz)
            1.15,   # Formant shift ratio
            280,    # New pitch median (Hz)
            1.1,    # Pitch range factor
            1.0,    # Duration factor
        )

        # 후처리 결과를 WAV로 저장
        with tempfile.NamedTemporaryFile(
                suffix=".wav", delete=False
        ) as output_file:
            output_path = output_file.name

        processed.save(output_path, "WAV")

        with open(output_path, "rb") as file:
            return file.read()

    finally:
        # 임시 파일 삭제
        for path in (input_path, output_path):
            if path and os.path.exists(path):
                os.remove(path)

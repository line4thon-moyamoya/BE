from typing import Annotated

from fastapi import APIRouter, File, Form, HTTPException, UploadFile
from fastapi.responses import Response
from pydantic import BaseModel, Field, ValidationError
from starlette.concurrency import run_in_threadpool

from song_mixer import SongMixError, mix_song

router = APIRouter()
MAX_BACKING_BYTES = 20 * 1024 * 1024
MAX_VOICE_BYTES = 10 * 1024 * 1024
MAX_TOTAL_BYTES = 50 * 1024 * 1024


class Placement(BaseModel):
    audioIndex: int = Field(ge=0, le=24, strict=True)
    startSec: float = Field(ge=0, le=120, allow_inf_nan=False)
    availableSec: float | None = Field(default=None, gt=0, le=120, allow_inf_nan=False)


class MixPlan(BaseModel):
    placements: list[Placement] = Field(min_length=1, max_length=25)


async def read_audio(upload: UploadFile, limit: int) -> bytes:
    data = await upload.read(limit + 1)
    if not data or len(data) > limit:
        raise HTTPException(status_code=400, detail="오디오 파일 크기가 올바르지 않습니다.")
    return data


@router.post("/songs/mix", response_class=Response)
async def mix_song_audio(
    backing: Annotated[UploadFile, File()],
    voices: Annotated[list[UploadFile], File()],
    metadata: Annotated[str, Form()],
):
    if len(metadata) > 16 * 1024 or not 1 <= len(voices) <= 25:
        raise HTTPException(status_code=400, detail="노래 합성 요청이 올바르지 않습니다.")
    try:
        plan = MixPlan.model_validate_json(metadata)
    except ValidationError as exc:
        raise HTTPException(status_code=400, detail="음성 배치 정보가 올바르지 않습니다.") from exc
    if any(placement.audioIndex >= len(voices) for placement in plan.placements):
        raise HTTPException(status_code=400, detail="음성 파일 인덱스가 올바르지 않습니다.")

    backing_data = await read_audio(backing, MAX_BACKING_BYTES)
    voice_data = []
    total_size = len(backing_data)
    for voice in voices:
        data = await read_audio(voice, MAX_VOICE_BYTES)
        if data[:4] != b"RIFF" or data[8:12] != b"WAVE":
            raise HTTPException(status_code=400, detail="단어 음성은 WAV 파일이어야 합니다.")
        total_size += len(data)
        if total_size > MAX_TOTAL_BYTES:
            raise HTTPException(status_code=400, detail="노래 합성 요청 크기가 너무 큽니다.")
        voice_data.append(data)
    try:
        audio = await run_in_threadpool(
            mix_song, backing_data, voice_data,
            [(placement.audioIndex, placement.startSec, placement.availableSec)
             for placement in plan.placements],
        )
    except SongMixError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    return Response(content=audio, media_type="audio/wav")

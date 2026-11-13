from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.responses import Response

from audio_processor import process_audio
from song_router import router as song_router

app = FastAPI(title="Moyamoya TTS Processor")
app.include_router(song_router)


@app.post("/process")
async def process_tts(audio: UploadFile = File(...)):

    if audio.content_type not in (
            "audio/wav",
            "audio/x-wav",
            "application/octet-stream",
    ):
        raise HTTPException(
            status_code=415,
            detail="WAV 파일만 지원합니다.",
        )

    data = await audio.read()

    if not data or len(data) > 10 * 1024 * 1024:
        raise HTTPException(
            status_code=400,
            detail="음성 파일 크기가 올바르지 않습니다.",
        )

    try:
        processed_audio = process_audio(data)
    except Exception as exc:
        raise HTTPException(
            status_code=422,
            detail="음성 후처리에 실패했습니다.",
        ) from exc

    return Response(
        content=processed_audio,
        media_type="audio/wav",
    )

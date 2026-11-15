FROM python:3.11-slim-bookworm

WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends libsndfile1 libgomp1 ffmpeg && rm -rf /var/lib/apt/lists/*
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
RUN groupadd --system processor && useradd --system --gid processor processor
COPY --chown=processor:processor main.py audio_processor.py song_router.py song_mixer.py song_voice_processor.py ./
USER processor:processor
EXPOSE 8001
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8001"]

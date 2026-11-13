package com.moyamoya.domain.song.service;

import java.util.EnumMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

final class SongGenerationMetrics {
    enum Stage { BACKING, TTS, PROCESSING, QUALITY, MIXING, UPLOAD, WORD_DOWNLOAD, WORD_UPLOAD }

    private final String id = UUID.randomUUID().toString();
    private final long startedAt = System.nanoTime();
    private final EnumMap<Stage, Long> durations = new EnumMap<>(Stage.class);
    private int inputs;
    private int ttsCalls;
    private int qualityRetries;
    private int exhaustedInputs;
    private Stage failedStage;
    private int cacheHits;
    private int cacheMisses;

    <T> T measure(Stage stage, Supplier<T> action) {
        long start = System.nanoTime();
        try {
            return action.get();
        } catch (RuntimeException exception) {
            failedStage = stage;
            throw exception;
        } finally {
            durations.merge(stage, System.nanoTime() - start, Long::sum);
        }
    }

    void inputStarted() { inputs++; }
    void ttsStarted(int attempt) {
        ttsCalls++;
        if (attempt > 1) {
            qualityRetries++;
        }
    }
    void exhausted() { exhaustedInputs++; }
    int exhaustedInputs() { return exhaustedInputs; }
    void cacheHit() { cacheHits++; }
    void cacheMiss() { cacheMisses++; }

    String summary() {
        return "mixId=" + id + ", inputs=" + inputs + ", ttsCalls=" + ttsCalls
                + ", qualityRetries=" + qualityRetries + ", exhaustedInputs=" + exhaustedInputs
                + ", backingMs=" + millis(Stage.BACKING) + ", ttsMs=" + millis(Stage.TTS)
                + ", processingMs=" + millis(Stage.PROCESSING) + ", qualityMs=" + millis(Stage.QUALITY)
                + ", mixingMs=" + millis(Stage.MIXING) + ", uploadMs=" + millis(Stage.UPLOAD)
                + ", cacheHits=" + cacheHits + ", cacheMisses=" + cacheMisses
                + ", wordDownloadMs=" + millis(Stage.WORD_DOWNLOAD) + ", wordUploadMs=" + millis(Stage.WORD_UPLOAD)
                + ", totalMs=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                + ", failedStage=" + failedStage;
    }

    private long millis(Stage stage) {
        return TimeUnit.NANOSECONDS.toMillis(durations.getOrDefault(stage, 0L));
    }
}

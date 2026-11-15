package com.moyamoya.domain.song.service;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class SongSyllableExecutor {
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, 2, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(20),
            Thread.ofPlatform().name("song-syllable-", 0).daemon(true).factory(),
            new ThreadPoolExecutor.AbortPolicy());

    Future<SongTtsService.Result> submit(Callable<SongTtsService.Result> task) {
        return executor.submit(task);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                cancelQueuedTasks();
            }
        } catch (InterruptedException exception) {
            cancelQueuedTasks();
            Thread.currentThread().interrupt();
        }
    }

    private void cancelQueuedTasks() {
        for (Runnable task : executor.shutdownNow()) {
            if (task instanceof Future<?> future) {
                future.cancel(true);
            }
        }
    }
}

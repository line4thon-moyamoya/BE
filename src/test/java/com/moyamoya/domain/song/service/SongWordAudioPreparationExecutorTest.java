package com.moyamoya.domain.song.service;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SongWordAudioPreparationExecutorTest {
    @Test
    void runsOffRequestThreadAndRejectsFullQueueWithoutRunningOnCaller() throws Exception {
        var executor = new SongWordAudioPreparationExecutor();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var worker = new AtomicReference<Thread>();
        try {
            executor.execute(() -> {
                worker.set(Thread.currentThread());
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(worker.get()).isNotSameAs(Thread.currentThread());
            for (int index = 0; index < 20; index++) {
                executor.execute(() -> {});
            }
            assertThatThrownBy(() -> executor.execute(() -> fail("must not run on caller")))
                    .isInstanceOf(RejectedExecutionException.class);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}

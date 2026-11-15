package com.moyamoya.domain.song.service;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SongSyllableExecutorTest {
    @Test
    void limitsAllSubmittedTasksToTwoWorkers() throws Exception {
        var executor = new SongSyllableExecutor();
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var active = new AtomicInteger();
        var maximum = new AtomicInteger();
        var tasks = new ArrayList<Future<SongTtsService.Result>>();
        try {
            for (int i = 0; i < 8; i++) {
                tasks.add(executor.submit(() -> {
                    int running = active.incrementAndGet();
                    maximum.accumulateAndGet(running, Math::max);
                    entered.countDown();
                    try {
                        assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                        assertThat(Thread.currentThread().getName()).startsWith("song-syllable-");
                        return new SongTtsService.Result(new byte[]{1}, true);
                    } finally {
                        active.decrementAndGet();
                    }
                }));
            }
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(active.get()).isEqualTo(2);
            assertThat(tasks).allMatch(task -> !task.isDone());
            release.countDown();
            for (var task : tasks) {
                assertThat(task.get(5, TimeUnit.SECONDS).qualityPassed()).isTrue();
            }
            assertThat(maximum.get()).isEqualTo(2);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}

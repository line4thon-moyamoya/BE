package com.moyamoya.domain.song.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class SongSyllableExecutorTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancelsRemovedTasksAndReleasesWaitingCallerOnForcedShutdown(boolean interrupted) throws Exception {
        var executor = new SongSyllableExecutor();
        var pool = mock(ThreadPoolExecutor.class);
        ReflectionTestUtils.setField(executor, "executor", pool);
        var queued = new FutureTask<SongTtsService.Result>(() -> {
            fail("Queued task must not run");
            return null;
        });
        when(pool.shutdownNow()).thenReturn(List.of(queued));
        if (interrupted) {
            when(pool.awaitTermination(60, TimeUnit.SECONDS)).thenThrow(new InterruptedException());
        } else {
            when(pool.awaitTermination(60, TimeUnit.SECONDS)).thenReturn(false);
        }
        var waiting = new CountDownLatch(1);
        try (var caller = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var result = caller.submit(() -> {
                waiting.countDown();
                assertThatThrownBy(queued::get).isInstanceOf(CancellationException.class);
            });
            try {
                assertThat(waiting.await(5, TimeUnit.SECONDS)).isTrue();
                executor.shutdown();
                assertThat(Thread.currentThread().isInterrupted()).isEqualTo(interrupted);
                assertThat(queued.isCancelled()).isTrue();
            } finally {
                Thread.interrupted();
                queued.cancel(true);
            }
            result.get(5, TimeUnit.SECONDS);
        }
        verify(pool).shutdown();
        verify(pool).shutdownNow();
    }

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

package com.moyamoya.domain.song.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.song.model.SongWordAudioPreparationRequested;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

class SongWordAudioPreparationListenerTest {
    @Test
    void queuesAfterCommitAndDoesNotWaitForAudioGeneration() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var executor = context.getBean(SongWordAudioPreparationExecutor.class);
            var audio = context.getBean(SongWordAudioService.class);
            var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            transaction.executeWithoutResult(status -> {
                context.publishEvent(new SongWordAudioPreparationRequested("학교"));
                verifyNoInteractions(executor, audio);
            });
            var task = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).execute(task.capture());
            verifyNoInteractions(audio);
            task.getValue().run();
            verify(audio).prepare("학교");
        }
    }

    @Test
    void rollbackAndEventsOutsideTransactionDoNotQueueWork() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            context.publishEvent(new SongWordAudioPreparationRequested("학교"));
            new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
                context.publishEvent(new SongWordAudioPreparationRequested("학교"));
                status.setRollbackOnly();
            });
            verifyNoInteractions(context.getBean(SongWordAudioPreparationExecutor.class),
                    context.getBean(SongWordAudioService.class));
        }
    }

    @Test
    void fullQueueDoesNotFailCommitOrLeaveWordPending() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var executor = context.getBean(SongWordAudioPreparationExecutor.class);
            var audio = context.getBean(SongWordAudioService.class);
            doThrow(new RejectedExecutionException("full")).when(executor).execute(any());
            var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            for (int index = 0; index < 2; index++) {
                assertThatCode(() -> transaction.executeWithoutResult(status ->
                        context.publishEvent(new SongWordAudioPreparationRequested("학교"))))
                        .doesNotThrowAnyException();
            }
            verify(executor, times(2)).execute(any());
            verifyNoInteractions(audio);
        }
    }

    @Test
    void duplicatePendingRequestsShareOneTaskAndFailedTaskCanBeRequestedAgain() {
        var executor = mock(SongWordAudioPreparationExecutor.class);
        var audio = mock(SongWordAudioService.class);
        var listener = new SongWordAudioPreparationListener(executor, audio);
        var event = new SongWordAudioPreparationRequested("학교");
        listener.onRequested(event);
        listener.onRequested(event);
        var task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(task.capture());
        doThrow(new IllegalStateException("TTS unavailable")).when(audio).prepare("학교");
        assertThatCode(task.getValue()::run).doesNotThrowAnyException();
        listener.onRequested(event);
        verify(executor, times(2)).execute(any());
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean PlatformTransactionManager transactionManager() { return new SongGenerationServiceTest.LocalTransactions(); }
        @Bean SongWordAudioPreparationExecutor executor() { return mock(SongWordAudioPreparationExecutor.class); }
        @Bean SongWordAudioService audio() { return mock(SongWordAudioService.class); }
        @Bean SongWordAudioPreparationListener listener(SongWordAudioPreparationExecutor executor, SongWordAudioService audio) {
            return new SongWordAudioPreparationListener(executor, audio);
        }
    }
}

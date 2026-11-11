package com.moyamoya.domain.song.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.song.model.SongGenerationRequested;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

class SongGenerationListenerTest {
    @Test
    void queuesOnlyAfterCommitAndRunsGenerationInSubmittedTask() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var executor = context.getBean(SongGenerationExecutor.class);
            var generation = context.getBean(SongGenerationService.class);
            var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            transaction.executeWithoutResult(status -> {
                context.publishEvent(new SongGenerationRequested(12L));
                verifyNoInteractions(executor, generation);
            });
            var task = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).execute(task.capture());
            verifyNoInteractions(generation);
            task.getValue().run();
            verify(generation).generate(12L);
        }
    }

    @Test
    void rollbackDoesNotQueueGeneration() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
                context.publishEvent(new SongGenerationRequested(12L));
                status.setRollbackOnly();
            });
            verifyNoInteractions(context.getBean(SongGenerationExecutor.class), context.getBean(SongGenerationService.class));
        }
    }

    @Test
    void eventOutsideTransactionDoesNotStartGeneration() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            context.publishEvent(new SongGenerationRequested(12L));
            verifyNoInteractions(context.getBean(SongGenerationExecutor.class), context.getBean(SongGenerationService.class));
        }
    }

    @Test
    void fullQueueMarksFailedWithoutThrowingAfterCommit() {
        var executor = mock(SongGenerationExecutor.class);
        var generation = mock(SongGenerationService.class);
        doThrow(new RejectedExecutionException("full")).when(executor).execute(any());
        var listener = new SongGenerationListener(executor, generation);
        assertThatCode(() -> listener.onRequested(new SongGenerationRequested(12L))).doesNotThrowAnyException();
        verify(generation).markFailed(12L);
        verify(generation, never()).generate(any());
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean PlatformTransactionManager transactionManager() { return new SongGenerationServiceTest.LocalTransactions(); }
        @Bean SongGenerationExecutor executor() { return mock(SongGenerationExecutor.class); }
        @Bean SongGenerationService generationService() { return mock(SongGenerationService.class); }
        @Bean SongGenerationListener listener(SongGenerationExecutor executor, SongGenerationService service) {
            return new SongGenerationListener(executor, service);
        }
    }
}

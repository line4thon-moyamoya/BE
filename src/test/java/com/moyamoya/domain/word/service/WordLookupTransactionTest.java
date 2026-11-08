package com.moyamoya.domain.word.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.question.service.CandidateTtsService;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.word.entity.LearnedWord;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.repository.LearnedWordRepository;
import com.moyamoya.domain.word.repository.WordRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class WordLookupTransactionTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lookupCommitsBeforeAudioTransactionStarts(boolean cardDetail) {
        var transactions = new RecordingTransactionManager();
        ChildRepository children = mock(ChildRepository.class);
        WordRepository words = mock(WordRepository.class);
        LearnedWordRepository cards = mock(LearnedWordRepository.class);
        CandidateTtsService tts = mock(CandidateTtsService.class);
        EntityManager em = mock(EntityManager.class);
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(7L);
        Child child = Child.create(owner, "지우", AgeGroup.AGE_5);
        Word word = Word.createExternal("적금", "기본 뜻", null, "어린 설명", "다섯 살 설명",
                null, null, "노래", null, "image.png", null, null, null);
        ReflectionTestUtils.setField(word, "id", 1L);
        when(children.findById(2L)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            return Optional.of(child);
        });
        if (cardDetail) {
            LearnedWord card = mock(LearnedWord.class);
            when(cards.findByIdAndChild_Id(35L, 2L)).thenReturn(Optional.of(card));
            when(card.getWord()).thenAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
                return word;
            });
            when(card.getId()).thenReturn(35L);
        } else {
            when(words.findById(1L)).thenReturn(Optional.of(word));
        }
        when(em.find(Word.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenAnswer(invocation -> {
            assertThat(transactions.events).containsExactly("read begin", "commit", "write begin");
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            return word;
        });
        doAnswer(invocation -> {
            assertThat(transactions.events).containsExactly("read begin", "commit", "write begin");
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            ReflectionTestUtils.setField(word, "descriptionAudioAge5Url", "five.wav");
            return null;
        }).when(tts).ensureStoredAudio(word, AgeGroup.AGE_5);
        when(tts.resolveAudioUrl("five.wav")).thenReturn("https://audio.test/five.wav");

        WordLookupService lookup = proxy(new WordLookupService(children, words, cards), transactions);
        WordDescriptionAudioService audio = proxy(new WordDescriptionAudioService(tts, em), transactions);
        if (cardDetail) {
            var response = proxy(new WordCardService(lookup, audio), transactions).getWordCardDetail(7L, 2L, 35L);
            assertThat(response.learnedWordId()).isEqualTo(35L);
            assertThat(response.imageUrl()).isEqualTo("image.png");
            assertThat(response.descriptionAudioUrl()).isEqualTo("https://audio.test/five.wav");
        } else {
            var response = proxy(new WordService(lookup, audio), transactions).getWordDescription(7L, 1L, 2L);
            assertThat(response.descriptionAudioUrl()).isEqualTo("https://audio.test/five.wav");
        }
        assertThat(transactions.events).containsExactly("read begin", "commit", "write begin", "commit");
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        verify(tts).ensureStoredAudio(word, AgeGroup.AGE_5);
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target, RecordingTransactionManager transactions) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        return (T) factory.getProxy();
    }

    // 실제 Spring 트랜잭션 경계를 검사하며 DB·외부 음성 서비스는 사용하지 않음
    private static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private final List<String> events = new ArrayList<>();
        private boolean active;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return active;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            assertThat(active).isFalse();
            active = true;
            events.add(definition.isReadOnly() ? "read begin" : "write begin");
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            events.add("commit");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            events.add("rollback");
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            active = false;
        }
    }
}

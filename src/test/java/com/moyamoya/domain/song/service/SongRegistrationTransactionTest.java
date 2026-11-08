package com.moyamoya.domain.song.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.song.entity.Song;
import com.moyamoya.domain.song.repository.SongRepository;
import com.moyamoya.domain.song.repository.SongWordRepository;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.word.entity.LearnedWord;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.repository.LearnedWordRepository;
import com.moyamoya.domain.word.repository.WordRepository;
import com.moyamoya.domain.word.service.WordCardAcquisitionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SongRegistrationTransactionTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cardAndSongUseOneTransactionAndRegistrationFailureRollsBack(boolean fail) {
        var transactions = new RecordingTransactionManager();
        var children = mock(ChildRepository.class);
        var words = mock(WordRepository.class);
        var cards = mock(LearnedWordRepository.class);
        var songs = mock(SongRepository.class);
        var songWords = mock(SongWordRepository.class);
        var owner = mock(User.class);
        when(owner.getId()).thenReturn(7L);
        Child child = Child.create(owner, "지우", AgeGroup.AGE_5);
        ReflectionTestUtils.setField(child, "id", 2L);
        Word word = mock(Word.class);
        List<LearnedWord> selected = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            selected.add(LearnedWord.create(child, word, false));
        }
        when(children.findByIdForUpdate(2L)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            transactions.events.add("lock child");
            return Optional.of(child);
        });
        when(words.findById(1L)).thenReturn(Optional.of(word));
        when(cards.save(any())).thenAnswer(invocation -> {
            LearnedWord card = invocation.getArgument(0);
            selected.add(card);
            transactions.events.add("save card");
            return card;
        });
        when(cards.countUnusedForSongByChildId(2L)).thenAnswer(invocation -> (long) selected.size());
        when(cards.findUnusedForSongByChildId(eq(2L), any())).thenReturn(selected);
        when(songs.save(any())).thenAnswer(invocation -> {
            Song song = invocation.getArgument(0);
            ReflectionTestUtils.setField(song, "id", 12L);
            transactions.events.add("save song");
            return song;
        });
        when(songWords.saveAll(anyList())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            transactions.events.add("save song words");
            if (fail) {
                throw new IllegalStateException("save failed");
            }
            return invocation.getArgument(0);
        });
        var registration = proxy(new SongRegistrationService(cards, songs, songWords), transactions);
        var acquisition = proxy(new WordCardAcquisitionService(children, words, cards, registration), transactions);

        if (fail) {
            assertThatThrownBy(() -> acquisition.acquireCard(7L, 2L, 1L, false))
                    .isInstanceOf(IllegalStateException.class);
        } else {
            var result = acquisition.acquireCard(7L, 2L, 1L, false);
            assertThat(result.couponCount()).isZero();
            assertThat(result.songId()).isEqualTo(12L);
        }

        assertThat(transactions.events).containsExactly("begin", "lock child", "save card", "save song",
                "save song words", fail ? "rollback" : "commit");
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void registrationCannotStartWithoutAcquisitionTransaction() {
        var cards = mock(LearnedWordRepository.class);
        var songs = mock(SongRepository.class);
        var songWords = mock(SongWordRepository.class);
        var service = proxy(new SongRegistrationService(cards, songs, songWords), new RecordingTransactionManager());

        assertThatThrownBy(() -> service.register(mock(Child.class)))
                .isInstanceOf(IllegalTransactionStateException.class);
        verifyNoInteractions(cards, songs, songWords);
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

    private static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private final List<String> events = new ArrayList<>();
        private boolean active;

        @Override
        protected Object doGetTransaction() { return new Object(); }

        @Override
        protected boolean isExistingTransaction(Object transaction) { return active; }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            active = true;
            events.add("begin");
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) { events.add("commit"); }

        @Override
        protected void doRollback(DefaultTransactionStatus status) { events.add("rollback"); }

        @Override
        protected void doSetRollbackOnly(DefaultTransactionStatus status) {}

        @Override
        protected void doCleanupAfterCompletion(Object transaction) { active = false; }
    }
}

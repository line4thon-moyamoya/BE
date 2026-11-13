package com.moyamoya.domain.song.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.song.dto.response.SongMixResponse;
import com.moyamoya.domain.song.entity.Song;
import com.moyamoya.domain.song.entity.SongStatus;
import com.moyamoya.domain.song.entity.SongWord;
import com.moyamoya.domain.song.repository.SongRepository;
import com.moyamoya.domain.song.repository.SongWordRepository;
import com.moyamoya.domain.word.entity.LearnedWord;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.global.s3.S3Service;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SongGenerationServiceTest {
    private final SongRepository songs = mock(SongRepository.class);
    private final SongWordRepository words = mock(SongWordRepository.class);
    private final SongMixingService mixing = mock(SongMixingService.class);
    private final S3Service audio = mock(S3Service.class);
    private final SongGenerationService service = new SongGenerationService(songs, words, mixing, audio,
            new LocalTransactions());
    private final Song song = Song.create(mock(Child.class), "test song");
    private final List<String> texts = List.of("금", "적금", "소방차", "알림장", "국회의원");
    private static final String KEY = "local/songs/generated/test.wav";

    private void generatingSong() {
        when(songs.findById(12L)).thenReturn(Optional.of(song));
        when(songs.findByIdForUpdate(12L)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return Optional.of(song);
        });
    }

    private void assignedWords() {
        var rows = IntStream.range(0, 5).mapToObj(index -> {
            Word word = mock(Word.class);
            when(word.getWord()).thenReturn(texts.get(index));
            return SongWord.create(song, LearnedWord.create(song.getChild(), word, false), index + 1);
        }).toList();
        when(words.findAllBySong_IdOrderByWordOrderAsc(12L)).thenReturn(rows);
    }

    @Test
    void mixesAssignedWordsOutsideTransactionAndPersistsOnlyKeyAsReady() {
        generatingSong();
        assignedWords();
        when(mixing.mix(texts)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new SongMixResponse(KEY, "https://expires.test/audio");
        });

        service.generate(12L);

        assertThat(song.getStatus()).isEqualTo(SongStatus.READY);
        assertThat(song.getAudioUrl()).isEqualTo(KEY);
        var order = inOrder(words, mixing, songs);
        order.verify(words).findAllBySong_IdOrderByWordOrderAsc(12L);
        order.verify(mixing).mix(texts);
        order.verify(songs).findByIdForUpdate(12L);
        verifyNoInteractions(audio);
    }

    @Test
    void transientFailureRetriesOnceWhileGeneratingAndThenSucceeds() {
        generatingSong();
        assignedWords();
        when(mixing.mix(texts)).thenThrow(new IllegalStateException("timeout", new java.net.SocketTimeoutException()))
                .thenAnswer(invocation -> {
                    assertThat(song.getStatus()).isEqualTo(SongStatus.GENERATING);
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    return new SongMixResponse(KEY, "url");
                });
        service.generate(12L);
        verify(mixing, times(2)).mix(texts);
        assertThat(song.getStatus()).isEqualTo(SongStatus.READY);
        verifyNoInteractions(audio);
    }

    @Test
    void secondTransientFailureMarksFailedWithoutThirdAttempt() {
        generatingSong();
        assignedWords();
        when(mixing.mix(texts)).thenThrow(new IllegalStateException("timeout", new java.net.SocketTimeoutException()));
        service.generate(12L);
        verify(mixing, times(2)).mix(texts);
        assertThat(song.getStatus()).isEqualTo(SongStatus.FAILED);
        assertThat(song.getAudioUrl()).isNull();
    }

    @Test
    void interruptedWorkerDoesNotRetry() {
        generatingSong();
        assignedWords();
        when(mixing.mix(texts)).thenThrow(new IllegalStateException("timeout", new java.net.SocketTimeoutException()));
        Thread.currentThread().interrupt();
        try {
            service.generate(12L);
            verify(mixing).mix(texts);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void synthesisFailureMarksSongFailedWithoutChangingCards() {
        generatingSong();
        assignedWords();
        when(mixing.mix(anyList())).thenThrow(new IllegalStateException("TTS failed"));
        service.generate(12L);
        assertThat(song.getStatus()).isEqualTo(SongStatus.FAILED);
        assertThat(song.getAudioUrl()).isNull();
        verify(mixing).mix(texts);
        verifyNoInteractions(audio);
        verify(words, never()).deleteAll(anyList());
    }

    @Test
    void incompleteAssignmentFailsBeforeTts() {
        generatingSong();
        when(words.findAllBySong_IdOrderByWordOrderAsc(12L)).thenReturn(List.of());
        service.generate(12L);
        assertThat(song.getStatus()).isEqualTo(SongStatus.FAILED);
        verifyNoInteractions(mixing, audio);
    }

    @Test
    void missingSongDoesNotCallExternalServices() {
        when(songs.findById(12L)).thenReturn(Optional.empty());
        service.generate(12L);
        verifyNoInteractions(words, mixing, audio);
    }

    @ParameterizedTest
    @EnumSource(value = SongStatus.class, names = {"READY", "FAILED"})
    void completedOrFailedSongIsNotGeneratedAgain(SongStatus status) {
        if (status == SongStatus.READY) { song.markAsReady(KEY); } else { song.markAsFailed(); }
        when(songs.findById(12L)).thenReturn(Optional.of(song));
        service.generate(12L);
        verifyNoInteractions(words, mixing, audio);
        assertThat(song.getStatus()).isEqualTo(status);
    }

    @Test
    void readyWriteFailureMarksFailedAndCleansUpUploadedObject() {
        generatingSong();
        assignedWords();
        when(mixing.mix(texts)).thenReturn(new SongMixResponse(KEY, "url"));
        doThrow(new IllegalStateException("DB failed")).doReturn(Optional.of(song))
                .when(songs).findByIdForUpdate(12L);
        service.generate(12L);
        assertThat(song.getStatus()).isEqualTo(SongStatus.FAILED);
        verify(audio).deleteObject(KEY);
    }

    @Test
    void doesNotOverwriteExistingReadyResultAndDeletesUnusedNewObject() {
        generatingSong();
        assignedWords();
        when(mixing.mix(texts)).thenAnswer(invocation -> {
            song.markAsReady("local/songs/generated/existing.wav");
            return new SongMixResponse(KEY, "url");
        });
        service.generate(12L);
        assertThat(song.getAudioUrl()).endsWith("existing.wav");
        assertThat(song.getStatus()).isEqualTo(SongStatus.READY);
        verify(audio).deleteObject(KEY);
    }

    @Test
    void failureStatusWriteErrorDoesNotEscapeIntoCardRequest() {
        when(songs.findByIdForUpdate(12L)).thenThrow(new IllegalStateException("DB unavailable"));
        assertThat(service.markFailed(12L)).isFalse();
    }

    static class LocalTransactions extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) {}
        @Override protected void doRollback(DefaultTransactionStatus status) {}
    }
}

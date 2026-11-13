package com.moyamoya.domain.song.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.song.model.SongAudioPlacement;
import com.moyamoya.domain.song.repository.SongWordAudioRepository;
import com.moyamoya.domain.word.entity.Word;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SongWordAudioServiceTest {
    private final SongWordAudioRepository words = mock(SongWordAudioRepository.class);
    private final SongTtsService tts = mock(SongTtsService.class);
    private final PythonSongMixerClient mixer = mock(PythonSongMixerClient.class);
    private final SongAudioStorageService storage = mock(SongAudioStorageService.class);
    private final SongWordAudioService service = new SongWordAudioService(words, new SongSpeechPlanner(), tts, mixer, storage);
    private final Word word = mock(Word.class);
    private final byte[] syllable = {1};
    private final byte[] clip = {2};

    @BeforeEach
    void setup() {
        when(word.getId()).thenReturn(7L);
        when(words.findByWord("학교")).thenReturn(Optional.of(word));
    }

    @Test
    void cachedWordSkipsTtsAndProcessing() {
        when(word.getSongAudioUrl()).thenReturn("key");
        when(storage.isCurrentWordKey(7L, "학교", "key")).thenReturn(true);
        when(storage.downloadWord("key")).thenReturn(clip);
        var metrics = new SongGenerationMetrics();
        assertThat(service.getOrCreate("학교", metrics, new LinkedHashMap<>())).isSameAs(clip);
        assertThat(metrics.summary()).contains("cacheHits=1", "cacheMisses=0", "ttsCalls=0");
        verifyNoInteractions(tts, mixer);
        verify(words, never()).replaceAudioKey(any(), anyString(), any(), anyString());
    }

    @Test
    void savesWordClipWithOriginalInternalBeatsAndReusesSharedSyllables() {
        generatedAudio();
        var shared = new LinkedHashMap<String, SongWordAudioService.Voice>();
        assertThat(service.getOrCreate("학교", new SongGenerationMetrics(), shared)).isSameAs(clip);
        assertThat(service.getOrCreate("교", new SongGenerationMetrics(), shared)).isSameAs(clip);
        verify(tts).synthesize(eq("학"), any());
        verify(tts).synthesize(eq("교"), any());
        verifyNoMoreInteractions(tts);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SongAudioPlacement>> plans = ArgumentCaptor.forClass(List.class);
        verify(mixer, times(2)).composeWord(anyList(), plans.capture());
        assertThat(plans.getAllValues().getFirst()).containsExactly(
                new SongAudioPlacement(0, 0, 0.5), new SongAudioPlacement(1, 0.5, 0.49),
                new SongAudioPlacement(0, 0.99, 0.5), new SongAudioPlacement(1, 1.49, 2.51));
        verify(storage).uploadWord(7L, "학교", clip);
        verify(words).replaceAudioKey(7L, "학교", null, "saved");
    }

    @Test
    void subsequentRequestUsesPersistedKeyWithoutGeneratingAgain() {
        generatedAudio();
        var savedKey = new java.util.concurrent.atomic.AtomicReference<String>();
        when(word.getSongAudioUrl()).thenAnswer(invocation -> savedKey.get());
        when(words.replaceAudioKey(7L, "학교", null, "saved")).thenAnswer(invocation -> {
            savedKey.set("saved");
            return 1;
        });
        when(storage.isCurrentWordKey(7L, "학교", "saved")).thenReturn(true);
        when(storage.downloadWord("saved")).thenReturn(clip);
        service.getOrCreate("학교", new SongGenerationMetrics(), new LinkedHashMap<>());
        var metrics = new SongGenerationMetrics();
        assertThat(service.getOrCreate("학교", metrics, new LinkedHashMap<>())).isSameAs(clip);
        verify(mixer).composeWord(anyList(), anyList());
        verify(storage).uploadWord(7L, "학교", clip);
        assertThat(metrics.summary()).contains("cacheHits=1", "ttsCalls=0");
    }

    @Test
    void concurrentRequestsGenerateRegisteredWordOnlyOnce() throws Exception {
        generatedAudio();
        var savedKey = new java.util.concurrent.atomic.AtomicReference<String>();
        when(word.getSongAudioUrl()).thenAnswer(invocation -> savedKey.get());
        when(words.replaceAudioKey(7L, "학교", null, "saved")).thenAnswer(invocation -> {
            savedKey.set("saved");
            return 1;
        });
        when(storage.isCurrentWordKey(7L, "학교", "saved")).thenReturn(true);
        when(storage.downloadWord("saved")).thenReturn(clip);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        when(tts.synthesize(eq("학"), any())).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            return syllable;
        });
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.getOrCreate("학교", new SongGenerationMetrics(), new LinkedHashMap<>()));
            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> service.getOrCreate("학교", new SongGenerationMetrics(), new LinkedHashMap<>()));
            release.countDown();
            assertThat(first.get(5, java.util.concurrent.TimeUnit.SECONDS)).isSameAs(clip);
            assertThat(second.get(5, java.util.concurrent.TimeUnit.SECONDS)).isSameAs(clip);
        } finally {
            release.countDown();
        }
        verify(mixer).composeWord(anyList(), anyList());
        verify(storage).uploadWord(7L, "학교", clip);
    }

    @Test
    void staleVersionIsReplacedWithoutReadingOldAudio() {
        generatedAudio();
        when(word.getSongAudioUrl()).thenReturn("old-version");
        when(words.replaceAudioKey(7L, "학교", "old-version", "saved")).thenReturn(1);
        service.getOrCreate("학교", new SongGenerationMetrics(), new LinkedHashMap<>());
        verify(storage, never()).downloadWord(anyString());
        verify(words).replaceAudioKey(7L, "학교", "old-version", "saved");
    }

    @Test
    void missingS3ObjectRegeneratesAndReplacesPreviousKey() {
        generatedAudio();
        when(word.getSongAudioUrl()).thenReturn("missing");
        when(storage.isCurrentWordKey(7L, "학교", "missing")).thenReturn(true);
        when(storage.downloadWord("missing")).thenReturn(null);
        when(words.replaceAudioKey(7L, "학교", "missing", "saved")).thenReturn(1);
        assertThat(service.getOrCreate("학교", new SongGenerationMetrics(), new LinkedHashMap<>())).isSameAs(clip);
        verify(words).replaceAudioKey(7L, "학교", "missing", "saved");
    }

    @Test
    void exhaustedSyllableIsUsedButNeverPersistedEvenWhenSharedWithAnotherWord() {
        generatedAudio();
        when(words.findByWord("교")).thenReturn(Optional.of(word));
        when(tts.synthesize(eq("교"), any())).thenAnswer(invocation -> {
            invocation.<SongGenerationMetrics>getArgument(1).exhausted();
            return syllable;
        });
        var shared = new LinkedHashMap<String, SongWordAudioService.Voice>();
        assertThat(service.getOrCreate("학교", new SongGenerationMetrics(), shared)).isSameAs(clip);
        assertThat(service.getOrCreate("교", new SongGenerationMetrics(), shared)).isSameAs(clip);
        verify(storage, never()).uploadWord(any(), anyString(), any());
        verify(words, never()).replaceAudioKey(any(), anyString(), any(), anyString());
    }

    @Test
    void removesOnlyOwnUploadWhenAnotherWriterWins() {
        generatedAudio();
        when(words.replaceAudioKey(7L, "학교", null, "saved")).thenReturn(0);
        assertThat(service.getOrCreate("학교", new SongGenerationMetrics(), new LinkedHashMap<>())).isSameAs(clip);
        verify(storage).deleteWord("saved");
    }

    @Test
    void databaseFailureCleansUploadAndKeepsOriginalFailure() {
        generatedAudio();
        var failure = new IllegalStateException("database failed");
        when(words.replaceAudioKey(7L, "학교", null, "saved")).thenThrow(failure);
        assertThatThrownBy(() -> service.getOrCreate("학교", new SongGenerationMetrics(), new LinkedHashMap<>()))
                .isSameAs(failure);
        verify(storage).deleteWord("saved");
    }

    @Test
    void downloadOutageDoesNotTriggerPaidRegeneration() {
        when(word.getSongAudioUrl()).thenReturn("key");
        when(storage.isCurrentWordKey(7L, "학교", "key")).thenReturn(true);
        var failure = new IllegalStateException("S3 unavailable");
        when(storage.downloadWord("key")).thenThrow(failure);
        assertThatThrownBy(() -> service.getOrCreate("학교", new SongGenerationMetrics(), new LinkedHashMap<>()))
                .isSameAs(failure);
        verifyNoInteractions(tts, mixer);
    }

    @Test
    void longWordIsSpokenWholeWithoutTempoCompression() {
        generatedAudio();
        service.getOrCreate("어린이보호구역", new SongGenerationMetrics(), new LinkedHashMap<>());
        verify(tts).synthesize(eq("어린이보호구역"), any());
        verify(mixer).composeWord(List.of(syllable), List.of(new SongAudioPlacement(0, 0, null)));
        verify(storage, never()).uploadWord(any(), anyString(), any());
    }

    private void generatedAudio() {
        when(tts.synthesize(anyString(), any())).thenReturn(syllable);
        when(mixer.composeWord(anyList(), anyList())).thenReturn(clip);
        when(storage.uploadWord(7L, "학교", clip)).thenReturn("saved");
        when(words.replaceAudioKey(7L, "학교", null, "saved")).thenReturn(1);
    }
}

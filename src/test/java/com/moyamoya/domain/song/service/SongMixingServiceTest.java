package com.moyamoya.domain.song.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.aryEq;

import com.moyamoya.domain.song.model.SongAudioPlacement;
import com.moyamoya.domain.song.dto.response.SongMixResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SongMixingServiceTest {
    private final SongWordAudioService tts = mock(SongWordAudioService.class);
    private final PythonSongMixerClient mixer = mock(PythonSongMixerClient.class);
    private final List<String> words = List.of("적금", "금", "적금", "금", "적금");

    private final SongAudioStorageService storage = mock(SongAudioStorageService.class);
    private final SongMixingService service = new SongMixingService(new SongSpeechPlanner(), tts, mixer, storage);

    @Test
    void reusesDistinctWordClipsAndKeepsFiveWordStarts() {
        when(storage.downloadBacking()).thenReturn(new byte[]{1, 2});
        byte[] jeok = {3};
        byte[] geum = {4};
        byte[] song = {82, 73, 70, 70, 1};
        when(tts.getOrCreate(eq(words.get(0)), anyMap())).thenReturn(jeok);
        when(tts.getOrCreate(eq(words.get(1)), anyMap())).thenReturn(geum);
        when(mixer.mixWords(any(), anyList(), anyList())).thenReturn(song);

        var expected = new SongMixResponse("local/songs/generated/test.wav", "https://audio.test/song");
        when(storage.upload(song)).thenReturn(expected);
        var result = service.mix(words);

        verify(tts).getOrCreate(eq(words.get(0)), anyMap());
        verify(tts).getOrCreate(eq(words.get(1)), anyMap());
        verifyNoMoreInteractions(tts);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SongAudioPlacement>> placements = ArgumentCaptor.forClass(List.class);
        verify(mixer).mixWords(aryEq(new byte[]{1, 2}), eq(List.of(jeok, geum)), placements.capture());
        assertThat(placements.getValue()).containsExactly(
                new SongAudioPlacement(0, 12, null), new SongAudioPlacement(1, 16, null),
                new SongAudioPlacement(0, 20, null), new SongAudioPlacement(1, 24, null),
                new SongAudioPlacement(0, 28, null));
        assertThat(result).isEqualTo(expected);
        var order = inOrder(storage, tts, mixer);
        order.verify(storage).downloadBacking();
        order.verify(tts).getOrCreate(eq(words.get(0)), anyMap());
        order.verify(tts).getOrCreate(eq(words.get(1)), anyMap());
        order.verify(mixer).mixWords(any(), anyList(), anyList());
        order.verify(storage).upload(song);
    }

    @Test
    void backingFailureStopsBeforePaidTts() {
        when(storage.downloadBacking()).thenThrow(new IllegalStateException("S3 unavailable"));
        assertThatIllegalStateException().isThrownBy(() -> service.mix(words));
        verifyNoInteractions(tts, mixer);
        verify(storage, never()).upload(any());
    }

    @Test
    void invalidWordCountStopsBeforeS3OrTts() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.mix(List.of("word")));
        verifyNoInteractions(storage, tts, mixer);
    }

    @Test
    void ttsFailureDoesNotMixOrUpload() {
        when(storage.downloadBacking()).thenReturn(new byte[]{1});
        when(tts.getOrCreate(anyString(), anyMap())).thenThrow(new IllegalStateException("TTS failed"));
        assertThatIllegalStateException().isThrownBy(() -> service.mix(words));
        verifyNoInteractions(mixer);
        verify(storage, never()).upload(any());
    }

    @Test
    void emptyTtsDoesNotMixOrUpload() {
        when(storage.downloadBacking()).thenReturn(new byte[]{1});
        when(tts.getOrCreate(anyString(), anyMap())).thenReturn(new byte[0]);
        assertThatIllegalStateException().isThrownBy(() -> service.mix(words));
        verifyNoInteractions(mixer);
        verify(storage, never()).upload(any());
    }

    @Test
    void mixerFailureDoesNotUpload() {
        when(storage.downloadBacking()).thenReturn(new byte[]{1});
        when(tts.getOrCreate(anyString(), anyMap())).thenReturn(new byte[]{1});
        when(mixer.mixWords(any(), anyList(), anyList())).thenThrow(new IllegalStateException("mix failed"));
        assertThatIllegalStateException().isThrownBy(() -> service.mix(words));
        verify(storage, never()).upload(any());
    }

    @Test
    void uploadFailureIsNotReturnedAsSuccess() {
        when(storage.downloadBacking()).thenReturn(new byte[]{1});
        when(tts.getOrCreate(anyString(), anyMap())).thenReturn(new byte[]{1});
        when(mixer.mixWords(any(), anyList(), anyList())).thenReturn(new byte[]{2});
        when(storage.upload(any())).thenThrow(new IllegalStateException("upload failed"));
        assertThatIllegalStateException().isThrownBy(() -> service.mix(words));
    }
}

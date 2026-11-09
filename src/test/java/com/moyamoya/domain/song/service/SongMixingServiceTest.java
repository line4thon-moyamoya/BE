package com.moyamoya.domain.song.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.aryEq;

import com.moyamoya.domain.question.service.MoyaTtsService;
import com.moyamoya.domain.song.model.SongAudioPlacement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class SongMixingServiceTest {
    @TempDir Path directory;
    private final MoyaTtsService tts = mock(MoyaTtsService.class);
    private final PythonSongMixerClient mixer = mock(PythonSongMixerClient.class);
    private final List<String> words = List.of("적금", "금", "적금", "금", "적금");

    private SongMixingService service(Path backing) {
        return new SongMixingService(new SongSpeechPlanner(), tts, mixer,
                backing.toString(), directory.resolve("output").toString());
    }

    @Test
    void synthesizesEachDistinctSyllableOnceAndSavesReturnedWav() throws IOException {
        Path backing = Files.write(directory.resolve("backing.m4a"), new byte[]{1, 2});
        byte[] jeok = {3};
        byte[] geum = {4};
        byte[] song = {82, 73, 70, 70, 1};
        when(tts.synthesize("적")).thenReturn(jeok);
        when(tts.synthesize("금")).thenReturn(geum);
        when(mixer.mix(any(), anyList(), anyList())).thenReturn(song);

        var result = service(backing).mix(words);

        verify(tts, times(1)).synthesize("적");
        verify(tts, times(1)).synthesize("금");
        verifyNoMoreInteractions(tts);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SongAudioPlacement>> placements = ArgumentCaptor.forClass(List.class);
        verify(mixer).mix(aryEq(new byte[]{1, 2}), eq(List.of(jeok, geum)), placements.capture());
        assertThat(placements.getValue()).hasSize(16);
        assertThat(placements.getValue().subList(0, 4)).containsExactly(
                new SongAudioPlacement(0, 12, 0.5), new SongAudioPlacement(1, 12.5, 0.49),
                new SongAudioPlacement(0, 12.99, 0.5), new SongAudioPlacement(1, 13.49, 2.51));
        assertThat(placements.getValue()).contains(new SongAudioPlacement(0, 28, 0.5));
        assertThat(result.audio()).isEqualTo(song);
        assertThat(Files.readAllBytes(result.outputPath())).isEqualTo(song);
        assertThat(result.outputPath().getParent()).isEqualTo(directory.resolve("output"));
        assertThat(Files.readAllBytes(backing)).containsExactly(1, 2);
    }

    @Test
    void missingBackingFailsBeforePaidTts() {
        assertThatIllegalStateException().isThrownBy(() -> service(directory.resolve("missing.m4a")).mix(words));
        verifyNoInteractions(tts, mixer);
    }

    @Test
    void missingConfigurationFailsBeforePaidTts() {
        var service = new SongMixingService(new SongSpeechPlanner(), tts, mixer, "", directory.toString());
        assertThatIllegalStateException().isThrownBy(() -> service.mix(words));
        verifyNoInteractions(tts, mixer);
    }

    @Test
    void invalidWordCountFailsBeforePaidTts() {
        assertThatIllegalArgumentException().isThrownBy(() -> service(directory.resolve("missing")).mix(List.of("적금")));
        verifyNoInteractions(tts, mixer);
    }

    @Test
    void ttsFailureDoesNotRequestMixOrWriteResult() throws IOException {
        Path backing = Files.write(directory.resolve("backing.m4a"), new byte[]{1});
        when(tts.synthesize("적")).thenThrow(new IllegalStateException("TTS failed"));

        assertThatIllegalStateException().isThrownBy(() -> service(backing).mix(words));

        verifyNoInteractions(mixer);
        try (var outputs = Files.list(directory.resolve("output"))) {
            assertThat(outputs).isEmpty();
        }
    }

    @Test
    void emptyTtsDoesNotRequestMix() throws IOException {
        Path backing = Files.write(directory.resolve("backing.m4a"), new byte[]{1});
        when(tts.synthesize("적")).thenReturn(new byte[0]);

        assertThatIllegalStateException().isThrownBy(() -> service(backing).mix(words));
        verifyNoInteractions(mixer);
    }

    @Test
    void mixerFailureDoesNotWriteResult() throws IOException {
        Path backing = Files.write(directory.resolve("backing.m4a"), new byte[]{1});
        when(tts.synthesize(anyString())).thenReturn(new byte[]{1});
        when(mixer.mix(any(), anyList(), anyList())).thenThrow(new IllegalStateException("mix failed"));

        assertThatIllegalStateException().isThrownBy(() -> service(backing).mix(words));
        try (var outputs = Files.list(directory.resolve("output"))) {
            assertThat(outputs).isEmpty();
        }
    }
}

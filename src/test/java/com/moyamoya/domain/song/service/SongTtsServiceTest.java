package com.moyamoya.domain.song.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;

class SongTtsServiceTest {
    private final SongGoogleTtsService google = mock(SongGoogleTtsService.class);
    private final PythonSongMixerClient processor = mock(PythonSongMixerClient.class);
    private final SongTtsService service = new SongTtsService(google, processor);

    @Test
    void retriesQuietOriginalWithoutProcessingIt() throws Exception {
        byte[] quiet = wav(0.003, 0.28, 0.28, false);
        byte[] good = wav(0.2, 0.4, 0.32, false);
        when(google.synthesize("우")).thenReturn(quiet, good);
        when(processor.processVoice(good)).thenReturn(good);
        var metrics = new SongGenerationMetrics();
        assertThat(service.synthesize("우", metrics)).isSameAs(good);
        assertThat(metrics.summary()).contains("inputs=1", "ttsCalls=2", "qualityRetries=1", "exhaustedInputs=0");
        verify(google, times(2)).synthesize("우");
        verify(processor).processVoice(good);
        verifyNoMoreInteractions(processor);
    }

    @Test
    void stopsAfterThreeSilentResults() throws Exception {
        when(google.synthesize("우")).thenReturn(wav(0, 0.24, 0.24, false));
        var metrics = new SongGenerationMetrics();
        assertThatIllegalStateException().isThrownBy(() -> service.synthesize("우", metrics));
        assertThat(metrics.summary()).contains("inputs=1", "ttsCalls=3", "qualityRetries=2",
                "exhaustedInputs=1", "failedStage=QUALITY");
        verify(google, times(3)).synthesize("우");
        verifyNoInteractions(processor);
    }

    @Test
    void retriesWhenProcessingRemovesAudibleSpeech() throws Exception {
        byte[] good = wav(0.2, 0.4, 0.32, false);
        when(google.synthesize("우")).thenReturn(good);
        when(processor.processVoice(good)).thenReturn(wav(0, 0.24, 0.24, false), good);
        assertThat(service.synthesize("우", new SongGenerationMetrics())).isSameAs(good);
        verify(google, times(2)).synthesize("우");
    }

    @Test
    void doesNotRepeatAuthenticationOrNetworkExceptionInsideQualityRetry() {
        when(google.synthesize("우")).thenThrow(new IllegalStateException("authentication"));
        assertThatIllegalStateException().isThrownBy(() -> service.synthesize("우", new SongGenerationMetrics()));
        verify(google).synthesize("우");
        verifyNoInteractions(processor);
    }

    @Test
    void acceptsUnvoicedNoiseAndSpeechWithLongSilenceButRejectsClicks() throws Exception {
        assertThat(SongTtsService.hasAudibleSpeech(wav(0.04, 0.3, 0.12, true))).isTrue();
        assertThat(SongTtsService.hasAudibleSpeech(wav(0.2, 4, 0.12, false))).isTrue();
        assertThat(SongTtsService.hasAudibleSpeech(wav(0.8, 0.3, 0.005, false))).isFalse();
        assertThat(SongTtsService.hasAudibleSpeech(wav(0.003, 0.3, 0.3, false))).isFalse();
    }

    @Test
    void rejectsMalformedWavWithoutRetrying() {
        when(google.synthesize("우")).thenReturn(new byte[]{1, 2, 3});
        assertThatIllegalStateException().isThrownBy(() -> service.synthesize("우", new SongGenerationMetrics()));
        verify(google).synthesize("우");
        verifyNoInteractions(processor);
    }

    private byte[] wav(double amplitude, double duration, double activeDuration, boolean noise) throws Exception {
        int rate = 24000;
        int frames = (int) (rate * duration);
        byte[] pcm = new byte[frames * 2];
        var random = new java.util.Random(1);
        for (int i = 0; i < frames && i < rate * activeDuration; i++) {
            double value = noise ? random.nextDouble() * 2 - 1 : Math.sin(2 * Math.PI * 440 * i / rate);
            short sample = (short) (32767 * amplitude * value);
            pcm[i * 2] = (byte) sample;
            pcm[i * 2 + 1] = (byte) (sample >> 8);
        }
        var output = new ByteArrayOutputStream();
        try (var input = new AudioInputStream(new ByteArrayInputStream(pcm),
                new AudioFormat(rate, 16, 1, true, false), frames)) {
            AudioSystem.write(input, AudioFileFormat.Type.WAVE, output);
        }
        return output.toByteArray();
    }
}

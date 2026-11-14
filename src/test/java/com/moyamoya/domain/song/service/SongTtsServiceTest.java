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
    void retriesQuietOriginalAndUsesPassingProcessedVoice() throws Exception {
        byte[] quiet = wav(0.003, 0.28, 0.28, false);
        byte[] good = wav(0.2, 0.4, 0.32, false);
        when(google.synthesize("우")).thenReturn(quiet, good);
        when(processor.processVoice(quiet)).thenReturn(quiet);
        when(processor.processVoice(good)).thenReturn(good);
        var result = service.synthesize("우");
        assertThat(result.audio()).isSameAs(good);
        assertThat(result.qualityPassed()).isTrue();
        verify(google, times(2)).synthesize("우");
        verify(processor).processVoice(quiet);
        verify(processor).processVoice(good);
        verifyNoMoreInteractions(processor);
    }

    @Test
    void returnsSilentCandidateAfterThreeAttempts() throws Exception {
        byte[] silent = wav(0, 0.24, 0.24, false);
        when(google.synthesize("u")).thenReturn(silent);
        when(processor.processVoice(silent)).thenReturn(silent);
        var result = service.synthesize("u");
        assertThat(result.audio()).isSameAs(silent);
        assertThat(result.qualityPassed()).isFalse();
        verify(google, times(3)).synthesize("u");
        verify(processor, times(3)).processVoice(silent);
    }

    @Test
    void selectsBestProcessedCandidateEvenWhenOriginalIsLouder() throws Exception {
        byte[] quiet = wav(0.002, 0.3, 0.3, false);
        byte[] best = wav(0.008, 0.3, 0.3, false);
        byte[] loud = wav(0.2, 0.3, 0.3, false);
        byte[] silent = wav(0, 0.3, 0.3, false);
        when(google.synthesize("u")).thenReturn(quiet, quiet, loud);
        when(processor.processVoice(quiet)).thenReturn(quiet, best);
        when(processor.processVoice(loud)).thenReturn(silent);
        var result = service.synthesize("u");
        assertThat(result.audio()).isSameAs(best);
        assertThat(result.qualityPassed()).isFalse();
        verify(google, times(3)).synthesize("u");
    }

    @Test
    void prefersSustainedQuietSpeechOverBriefLoudClick() throws Exception {
        byte[] quiet = wav(0.006, 0.3, 0.3, false);
        byte[] click = wav(0.8, 0.3, 0.005, false);
        when(google.synthesize("우")).thenReturn(click, quiet, click);
        when(processor.processVoice(click)).thenReturn(click);
        when(processor.processVoice(quiet)).thenReturn(quiet);
        var result = service.synthesize("우");
        assertThat(result.audio()).isSameAs(quiet);
        assertThat(result.qualityPassed()).isFalse();
        verify(google, times(3)).synthesize("우");
    }

    @Test
    void doesNotHideProcessingFailureBehindEarlierCandidate() throws Exception {
        byte[] quiet = wav(0.003, 0.3, 0.3, false);
        var error = new IllegalStateException("processor unavailable");
        when(google.synthesize("우")).thenReturn(quiet);
        when(processor.processVoice(quiet)).thenReturn(quiet).thenThrow(error);
        assertThatThrownBy(() -> service.synthesize("우")).isSameAs(error);
        verify(google, times(2)).synthesize("우");
    }

    @Test
    void retriesWhenProcessingRemovesAudibleSpeech() throws Exception {
        byte[] good = wav(0.2, 0.4, 0.32, false);
        when(google.synthesize("우")).thenReturn(good);
        when(processor.processVoice(good)).thenReturn(wav(0, 0.24, 0.24, false), good);
        var result = service.synthesize("우");
        assertThat(result.audio()).isSameAs(good);
        assertThat(result.qualityPassed()).isTrue();
        verify(google, times(2)).synthesize("우");
    }

    @Test
    void doesNotRepeatAuthenticationOrNetworkExceptionInsideQualityRetry() {
        when(google.synthesize("우")).thenThrow(new IllegalStateException("authentication"));
        assertThatIllegalStateException().isThrownBy(() -> service.synthesize("우"));
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
        assertThatIllegalStateException().isThrownBy(() -> service.synthesize("우"));
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

package com.moyamoya.domain.word.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.question.service.MoyaTtsService;
import com.moyamoya.domain.question.exception.QuestionErrorCode;
import com.moyamoya.global.exception.CustomException;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RepetitionDialogueServiceTest {
    private final MoyaTtsService tts = mock(MoyaTtsService.class);
    private final RepetitionDialogueService service = new RepetitionDialogueService(tts);

    @ParameterizedTest
    @CsvSource({"true,우와! 적금! 잘했어!", "false,적금! 적금! 다음에 또 해보자!"})
    void repetitionReturnsResultTextAndBase64Wav(boolean correct, String text) {
        byte[] wav = {82, 73, 70, 70, 0, 1, 2};
        when(tts.synthesize(text)).thenReturn(wav);
        var result = service.repetition("적금", correct);
        assertThat(result.text()).isEqualTo(text);
        assertThat(Base64.getDecoder().decode(result.audioBase64())).isEqualTo(wav);
        assertThat(result.audioContentType()).isEqualTo("audio/wav");
        verify(tts).synthesize(text);
    }

    @ParameterizedTest
    @CsvSource({"true,새로운 단어 카드를 받았어! 수첩에서 확인해 봐!", "false,이미 수첩에 있는 단어야!"})
    void acquisitionUsesNewOrExistingCardText(boolean isNew, String text) {
        when(tts.synthesize(text)).thenReturn(new byte[]{1});
        var result = service.acquisition(isNew);
        assertThat(result.text()).isEqualTo(text);
        assertThat(result.audioContentType()).isEqualTo("audio/wav");
        verify(tts).synthesize(text);
    }

    @Test
    void ttsExceptionKeepsTextWithNullAudio() {
        when(tts.synthesize(anyString())).thenThrow(new CustomException(QuestionErrorCode.TTS_GENERATION_FAILED));
        var result = service.repetition("적금", false);
        assertThat(result.text()).isEqualTo("적금! 적금! 다음에 또 해보자!");
        assertThat(result.audioBase64()).isNull();
        assertThat(result.audioContentType()).isNull();
    }

    @Test
    void emptyOrNullAudioKeepsTextWithNullAudio() {
        when(tts.synthesize(anyString())).thenReturn(new byte[0], (byte[]) null);
        var first = service.repetition("적금", true);
        var second = service.acquisition(true);
        assertThat(first.text()).isEqualTo("우와! 적금! 잘했어!");
        assertThat(second.text()).isEqualTo("새로운 단어 카드를 받았어! 수첩에서 확인해 봐!");
        assertThat(first.audioBase64()).isNull();
        assertThat(first.audioContentType()).isNull();
        assertThat(second.audioBase64()).isNull();
        assertThat(second.audioContentType()).isNull();
    }

    @Test
    void oneDialogueFailureDoesNotSkipOtherDialogueAudio() {
        when(tts.synthesize("우와! 적금! 잘했어!")).thenThrow(new IllegalStateException("TTS unavailable"));
        when(tts.synthesize("이미 수첩에 있는 단어야!")).thenReturn(new byte[]{1, 2});
        assertThat(service.repetition("적금", true).audioBase64()).isNull();
        assertThat(service.acquisition(false).audioBase64()).isEqualTo("AQI=");
    }
}

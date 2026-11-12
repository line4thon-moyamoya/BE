package com.moyamoya.domain.word.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.question.service.CandidateTtsService;
import com.moyamoya.domain.word.entity.Word;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class WordDescriptionAudioServiceTest {

    private final CandidateTtsService candidateTtsService = mock(CandidateTtsService.class);
    private final WordDescriptionAudioService service = new WordDescriptionAudioService(candidateTtsService);

    private Word word(
            String desc3,
            String desc5,
            String definition,
            String wordAudioKey
    ) {
        return Word.createExternal(
                "적금",
                definition,
                null,
                desc3,
                desc5,
                null,
                null,
                null,
                null,
                wordAudioKey
        );
    }

    @ParameterizedTest
    @EnumSource(AgeGroup.class)
    void reusesExistingWordAudioForDescriptionLookup(AgeGroup ageGroup) {
        Word word = word("어린 설명", "다섯 살 설명", "기본 설명", "dev/word.wav");
        when(candidateTtsService.resolveAudioUrl("dev/word.wav"))
                .thenReturn("https://audio.test/word.wav");

        var result = service.resolve(word, ageGroup);

        assertThat(result.word()).isEqualTo("적금");
        assertThat(result.description())
                .isEqualTo(ageGroup == AgeGroup.AGE_3_4 ? "어린 설명" : "다섯 살 설명");
        assertThat(result.wordAudioUrl()).isEqualTo("https://audio.test/word.wav");
        verify(candidateTtsService, never()).ensureStoredAudio(word);
    }

    @ParameterizedTest
    @EnumSource(AgeGroup.class)
    void fallsBackToDefinitionWhenAgeDescriptionMissing(AgeGroup ageGroup) {
        Word word = word(" ", null, "기본 뜻", "dev/word.wav");
        when(candidateTtsService.resolveAudioUrl("dev/word.wav"))
                .thenReturn("https://audio.test/word.wav");

        var result = service.resolve(word, ageGroup);

        assertThat(result.description()).isEqualTo("기본 뜻");
        assertThat(result.wordAudioUrl()).isEqualTo("https://audio.test/word.wav");
    }

    @Test
    void generatesWordAudioWhenMissing() {
        Word word = word("설명", "설명", "기본 뜻", null);
        when(candidateTtsService.ensureStoredAudio(word))
                .thenReturn("dev/word.wav");
        when(candidateTtsService.resolveAudioUrl("dev/word.wav"))
                .thenReturn("https://audio.test/word.wav");
        var result = service.resolve(word, AgeGroup.AGE_5);
        assertThat(result.wordAudioUrl())
                .isEqualTo("https://audio.test/word.wav");
        verify(candidateTtsService).ensureStoredAudio(word);
        verify(candidateTtsService).resolveAudioUrl("dev/word.wav");
    }

    @Test
    void propagatesSigningFailure() {
        Word word = word("설명", "설명", "기본 뜻", "dev/word.wav");
        when(candidateTtsService.resolveAudioUrl("dev/word.wav"))
                .thenThrow(new IllegalStateException("sign failed"));

        assertThatThrownBy(() -> service.resolve(word, AgeGroup.AGE_5))
                .isInstanceOf(IllegalStateException.class);
        verify(candidateTtsService, never()).ensureStoredAudio(word);
        verify(candidateTtsService).resolveAudioUrl("dev/word.wav");
        verifyNoMoreInteractions(candidateTtsService);
    }
}

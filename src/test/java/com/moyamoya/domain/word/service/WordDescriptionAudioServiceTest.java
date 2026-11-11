package com.moyamoya.domain.word.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.question.service.CandidateTtsService;
import com.moyamoya.domain.question.service.MoyaTtsService;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.repository.WordRepository;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.s3.S3Service;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class WordDescriptionAudioServiceTest {
    private final MoyaTtsService tts = mock(MoyaTtsService.class);
    private final S3Service s3 = mock(S3Service.class);
    private final WordRepository words = mock(WordRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final WordDescriptionAudioService service = new WordDescriptionAudioService(
            new CandidateTtsService(tts, s3, words), em);

    private Word word(String desc3, String desc5, String audio3, String audio5) {
        Word word = Word.createExternal("적금", "기본 설명", null, desc3, desc5, null, null,
                "노래", null, null, "dev/word.wav", audio3, audio5);
        ReflectionTestUtils.setField(word, "id", 1L);
        return word;
    }

    private String generatedKey(AgeGroup age) {
        return "dev/words/audio/1/desc-age-" + (age == AgeGroup.AGE_3_4 ? "3-4" : "5") + ".wav";
    }

    private void generation(Word word) {
        when(em.find(Word.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(word);
        when(tts.synthesize(anyString())).thenReturn(new byte[]{1, 2, 3});
        when(s3.uploadWav(anyString(), any())).thenAnswer(invocation -> "dev/" + invocation.getArgument(0));
        when(s3.getPresignedUrl(anyString())).thenReturn("https://audio.test/signed.wav");
    }

    @ParameterizedTest
    @EnumSource(AgeGroup.class)
    void reusesExistingAudioAndSignsOnlyRequestedAge(AgeGroup age) {
        Word word = word("어린 설명", "다섯 살 설명", "dev/young.wav", "dev/five.wav");
        String key = age == AgeGroup.AGE_3_4 ? "dev/young.wav" : "dev/five.wav";
        when(s3.getPresignedUrl(key)).thenReturn("https://audio.test/signed.wav");

        var result = service.resolve(word, age);

        assertThat(result.wordId()).isEqualTo(1L);
        assertThat(result.description()).isEqualTo(age == AgeGroup.AGE_3_4 ? "어린 설명" : "다섯 살 설명");
        assertThat(result.descriptionAudioUrl()).isEqualTo("https://audio.test/signed.wav");
        verifyNoInteractions(tts, words, em);
        verify(s3).getPresignedUrl(key);
        verifyNoMoreInteractions(s3);
    }

    @ParameterizedTest
    @EnumSource(AgeGroup.class)
    void createsAndStoresOnlyMissingDescriptionThenReusesIt(AgeGroup age) {
        Word word = word("어린 설명", "다섯 살 설명", null, null);
        generation(word);

        var first = service.resolve(word, age);
        var second = service.resolve(word, age);

        assertThat(first.descriptionAudioUrl()).isEqualTo("https://audio.test/signed.wav");
        assertThat(second).isEqualTo(first);
        verify(tts).synthesize(age == AgeGroup.AGE_3_4 ? "어린 설명" : "다섯 살 설명");
        verify(s3).uploadWav(generatedKey(age).substring(4), new byte[]{1, 2, 3});
        verify(words).save(word);
        verify(s3, times(2)).getPresignedUrl(generatedKey(age));
        assertThat(age == AgeGroup.AGE_3_4 ? word.getDescriptionAudioAge5Url()
                : word.getDescriptionAudioAge3To4Url()).isNull();
    }

    @ParameterizedTest
    @EnumSource(AgeGroup.class)
    void fallsBackToDefinitionForBothDisplayedTextAndGeneratedSpeech(AgeGroup age) {
        Word word = word(null, " ", null, null);
        generation(word);

        var result = service.resolve(word, age);

        assertThat(result.description()).isEqualTo("기본 설명");
        verify(tts).synthesize("기본 설명");
    }

    @Test
    void rechecksAfterLockAndUsesAudioPreparedByAnotherRequest() {
        Word initial = word("설명", "설명", null, null);
        Word refreshed = word("설명", "설명", null, "dev/already-prepared.wav");
        when(em.find(Word.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(refreshed);
        when(s3.getPresignedUrl("dev/already-prepared.wav")).thenReturn("https://audio.test/ready.wav");

        assertThat(service.resolve(initial, AgeGroup.AGE_5).descriptionAudioUrl()).isEqualTo("https://audio.test/ready.wav");
        verifyNoInteractions(tts, words);
        verify(s3, never()).uploadWav(anyString(), any());
    }

    @Test
    void doesNotGenerateWhenAllDescriptionDataIsMissing() {
        Word word = mock(Word.class);
        var result = service.resolve(word, AgeGroup.AGE_5);
        assertThat(result.description()).isNull();
        assertThat(result.descriptionAudioUrl()).isNull();
        verifyNoInteractions(tts, s3, words, em);
    }

    @Test
    void rejectsWordDeletedBeforePreparation() {
        Word initial = word("설명", "설명", null, null);
        when(em.find(Word.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(null);
        assertThatThrownBy(() -> service.resolve(initial, AgeGroup.AGE_5))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode().getCode()).isEqualTo("WORD404"));
        verifyNoInteractions(tts, s3, words);
    }

    @Test
    void generationFailureDoesNotReturnSuccessWithSilentAudio() {
        Word word = word("설명", "설명", null, null);
        when(em.find(Word.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(word);
        when(tts.synthesize("설명")).thenThrow(new IllegalStateException("test failure"));
        assertThatThrownBy(() -> service.resolve(word, AgeGroup.AGE_5)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(s3, words);
    }

    @Test
    void uploadFailureDoesNotSaveAudioKey() {
        Word word = word("설명", "설명", null, null);
        when(em.find(Word.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(word);
        when(tts.synthesize("설명")).thenReturn(new byte[]{1});
        when(s3.uploadWav(anyString(), any())).thenThrow(new IllegalStateException("test failure"));
        assertThatThrownBy(() -> service.resolve(word, AgeGroup.AGE_5)).isInstanceOf(IllegalStateException.class);
        assertThat(word.getDescriptionAudioAge5Url()).isNull();
        verifyNoInteractions(words);
    }

    @Test
    void signingFailureIsPropagatedWithoutRegeneratingExistingAudio() {
        Word word = word("설명", "설명", null, "dev/five.wav");
        when(s3.getPresignedUrl("dev/five.wav")).thenThrow(new IllegalStateException("test failure"));
        assertThatThrownBy(() -> service.resolve(word, AgeGroup.AGE_5)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(tts, words, em);
    }
}

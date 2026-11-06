package com.moyamoya.domain.word.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.question.config.QuestionDataLoader;
import com.moyamoya.domain.question.model.QuestionWordData;
import com.moyamoya.domain.question.service.PronunciationSimilarity;
import com.moyamoya.domain.word.model.RepetitionEvaluation.Status;
import com.moyamoya.domain.word.repository.WordRepository;
import java.text.Normalizer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class RepetitionEvaluationServiceTest {
    private final QuestionDataLoader loader = mock(QuestionDataLoader.class);
    private final WordRepository words = mock(WordRepository.class);
    private final RepetitionEvaluationService service = new RepetitionEvaluationService(
            new PronunciationSimilarity(), loader, words);

    private QuestionWordData data(String word, String expected, String age3) {
        return new QuestionWordData(word, word, null, null, null, null, null,
                expected, age3, null, null, null, null, null, null, null);
    }

    @ParameterizedTest
    @EnumSource(AgeGroup.class)
    void exactTargetSucceedsWithoutRepositoryLookup(AgeGroup age) {
        var result = service.evaluate("적금", "적금", age);
        assertThat(result.status()).isEqualTo(Status.SUCCESS);
        assertThat(result.similarity()).isEqualTo(1.0);
        assertThat(result.isCorrect()).isTrue();
        verifyNoInteractions(loader, words);
    }

    @ParameterizedTest
    @ValueSource(strings = {" 적 금! ", "적금.", "적금?", "적금\u3000"})
    void ignoresSpacesAndPunctuation(String spoken) {
        assertThat(service.evaluate(spoken, "적금", AgeGroup.AGE_5).isCorrect()).isTrue();
    }

    @Test
    void normalizesDecomposedHangul() {
        String spoken = Normalizer.normalize("적금", Normalizer.Form.NFD);
        assertThat(service.evaluate(spoken, "적금", AgeGroup.AGE_5).isCorrect()).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "...?!"})
    void emptyRecognitionIsNoSpeech(String spoken) {
        var result = service.evaluate(spoken, "적금", AgeGroup.AGE_5);
        assertThat(result.status()).isEqualTo(Status.NO_SPEECH);
        assertThat(result.isCorrect()).isFalse();
        assertThat(result.similarity()).isZero();
        verifyNoInteractions(loader, words);
    }

    @ParameterizedTest
    @ValueSource(strings = {"저끔", "적끔", "저 끔!"})
    void acceptsExpectedPronunciationsIncludingMultipleAliases(String spoken) {
        when(loader.getWords()).thenReturn(List.of(data("적금", "저끔; 적끔|", null)));
        assertThat(service.evaluate(spoken, "적금", AgeGroup.AGE_5).isCorrect()).isTrue();
        verifyNoInteractions(words);
    }

    @Test
    void childPronunciationAliasIsIncludedOnlyForYoungerGroup() {
        when(loader.getWords()).thenReturn(List.of(data("적금", null, "완전히다른표현")));
        assertThat(service.evaluate("완전히다른표현", "적금", AgeGroup.AGE_3_4).isCorrect()).isTrue();
        assertThat(service.evaluate("완전히다른표현", "적금", AgeGroup.AGE_5).isCorrect()).isFalse();
    }

    @Test
    void explicitAcceptedAliasTakesPriorityOverOtherDictionaryEntry() {
        when(loader.getWords()).thenReturn(List.of(data("적금", "저금", null), data("저금", null, null)));
        assertThat(service.evaluate("저금", "적금", AgeGroup.AGE_5).isCorrect()).isTrue();
        verifyNoInteractions(words);
    }

    @ParameterizedTest
    @CsvSource({"0.8,true", "0.8001,true", "0.7999,false", "0.7,false"})
    void appliesThresholdIncludingBoundary(double score, boolean correct) {
        PronunciationSimilarity similarity = mock(PronunciationSimilarity.class);
        when(loader.getWords()).thenReturn(List.of());
        when(similarity.calculate("데금", "세금")).thenReturn(score);
        var evaluator = new RepetitionEvaluationService(similarity, loader, words);
        var result = evaluator.evaluate("데금", "세금", AgeGroup.AGE_5);
        assertThat(result.similarity()).isEqualTo(score);
        assertThat(result.isCorrect()).isEqualTo(correct);
    }

    @Test
    void rejectsExactOtherCsvWordEvenWithHighSimilarity() {
        when(loader.getWords()).thenReturn(List.of(data("적금", null, null), data("저금", null, null)));
        var result = service.evaluate("저금", "적금", AgeGroup.AGE_5);
        assertThat(result.similarity()).isGreaterThan(0.8);
        assertThat(result.status()).isEqualTo(Status.FAILURE);
        verifyNoInteractions(words);
    }

    @Test
    void rejectsExactOtherDatabaseWordEvenWithHighSimilarity() {
        when(loader.getWords()).thenReturn(List.of());
        when(words.existsByWord("저금")).thenReturn(true);
        var result = service.evaluate("저금", "적금", AgeGroup.AGE_5);
        assertThat(result.similarity()).isGreaterThan(0.8);
        assertThat(result.isCorrect()).isFalse();
    }

    @Test
    void externalTargetWithoutCsvUsesExistingSimilarityCalculator() {
        when(loader.getWords()).thenReturn(List.of());
        assertThat(service.evaluate("데금", "세금", AgeGroup.AGE_5).isCorrect()).isTrue();
        assertThat(service.evaluate("사과", "세금", AgeGroup.AGE_5).isCorrect()).isFalse();
        assertThat(service.evaluate("적응", "적금", AgeGroup.AGE_5).isCorrect()).isFalse();
    }

    @Test
    void doesNotTreatSubstringOfSentenceAsExactSuccess() {
        when(loader.getWords()).thenReturn(List.of());
        assertThat(service.evaluate("적금이 뭔지 모르겠어요", "적금", AgeGroup.AGE_5).isCorrect()).isFalse();
    }

    @Test
    void worksWithRealCsvPronunciationDataWithoutCallingExternalServices() throws Exception {
        QuestionDataLoader actualLoader = new QuestionDataLoader(new ObjectMapper());
        actualLoader.load();
        var evaluator = new RepetitionEvaluationService(new PronunciationSimilarity(), actualLoader, words);
        assertThat(evaluator.evaluate("저끔", "적금", AgeGroup.AGE_3_4).isCorrect()).isTrue();
        assertThat(evaluator.evaluate("적끔", "적금", AgeGroup.AGE_5).isCorrect()).isTrue();
        assertThat(evaluator.evaluate("조금", "적금", AgeGroup.AGE_5).isCorrect()).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "?!"})
    void rejectsMissingTargetWord(String target) {
        assertThatThrownBy(() -> service.evaluate("적금", target, AgeGroup.AGE_5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingAgeGroup() {
        assertThatThrownBy(() -> service.evaluate("적금", "적금", null)).isInstanceOf(IllegalArgumentException.class);
    }
}

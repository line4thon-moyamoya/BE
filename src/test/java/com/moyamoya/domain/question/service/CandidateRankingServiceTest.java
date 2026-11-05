package com.moyamoya.domain.question.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.question.config.QuestionDataLoader;
import com.moyamoya.domain.question.model.WordCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateRankingServiceTest {

    private CandidateRankingService rankingService;

    @BeforeEach
    void setUp() throws Exception {
        QuestionDataLoader loader =
                new QuestionDataLoader(new ObjectMapper());

        loader.load();

        rankingService = new CandidateRankingService(
                loader,
                new PronunciationSimilarity(),
                new PronunciationVariantGenerator(loader)
        );
    }

    @Test
    void exactMatchShouldRankFirst() {
        List<WordCandidate> candidates =
                rankingService.rankFirst(List.of("세금"));

        assertThat(candidates).isNotEmpty();
        assertThat(candidates.get(0).word()).isEqualTo("세금");
    }

    @Test
    void childPronunciationShouldFindOriginalWord() {
        List<WordCandidate> candidates =
                rankingService.rankFirst(List.of("데금"));

        assertThat(candidates)
                .extracting(WordCandidate::word)
                .contains("세금");
    }

    @Test
    void josaExpressionsShouldBeSearched() {
        List<WordCandidate> candidates =
                rankingService.rankFirst(
                        List.of("세금이", "세금")
                );

        assertThat(candidates)
                .extracting(WordCandidate::word)
                .contains("세금");
    }

    @Test
    void candidateCountShouldBeAtMostThree() {
        List<WordCandidate> candidates =
                rankingService.rankFirst(List.of("데금"));

        assertThat(candidates.size()).isLessThanOrEqualTo(3);
    }

    @Test
    void blankExpressionsShouldReturnEmpty() {
        List<WordCandidate> candidates =
                rankingService.rankFirst(List.of("", " "));

        assertThat(candidates).isEmpty();
    }

    @Test
    void originalExpressionShouldBePreservedWhenItIsAWord() {
        assertThat(
                rankingService.resolveTargetPronunciation(
                        List.of("세금", "세")
                )
        ).contains("세금");
    }
}


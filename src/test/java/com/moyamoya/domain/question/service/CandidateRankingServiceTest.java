package com.moyamoya.domain.question.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.question.config.QuestionDataLoader;
import com.moyamoya.domain.question.model.WordCandidate;
import com.moyamoya.domain.word.entity.HeardFrom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateRankingServiceTest {

    private CandidateRankingService rankingService;
    private BadWordFilterService badWordFilterService;

    @BeforeEach
    void setUp() throws Exception {
        QuestionDataLoader loader =
                new QuestionDataLoader(new ObjectMapper());

        loader.load();
        badWordFilterService = Mockito.mock(BadWordFilterService.class);
        Mockito.when(badWordFilterService.containsBlockedText(Mockito.any()))
                .thenReturn(false);

        rankingService = new CandidateRankingService(
                loader,
                new PronunciationSimilarity(),
                new PronunciationVariantGenerator(loader),
                new ContextScoringService(),
                badWordFilterService
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
    void calculateSecondPassScoreUsesAvailableSignals() {
        double withAllSignals = rankingService.calculateSecondPassScore(
                0.8,
                0.6,
                0.4
        );
        double withPronunciationOnly = rankingService.calculateSecondPassScore(
                0.8,
                null,
                null
        );

        assertThat(withAllSignals).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
        assertThat(withPronunciationOnly).isEqualTo(0.8);
    }

    @Test
    void secondRankingShouldExcludeFirstCandidateKeys() {
        List<WordCandidate> candidates = rankingService.rankSecond(
                List.of("데금"),
                "엄마가 세금 내야 된다고 했어. 데금이 뭐야?",
                HeardFrom.PARENTS,
                Set.of("CURATED:세금", "CURATED:소금", "CURATED:선거")
        );

        assertThat(candidates)
                .extracting(WordCandidate::candidateId)
                .doesNotContain("CURATED:세금", "CURATED:소금", "CURATED:선거");
    }

    @Test
    void blockedCandidateShouldBeExcluded() {
        Mockito.when(
                badWordFilterService.containsBlockedText("세금")
        ).thenReturn(true);

        List<WordCandidate> candidates =
                rankingService.rankFirst(List.of("세금"));

        assertThat(candidates)
                .extracting(WordCandidate::word)
                .doesNotContain("세금");
    }

    @Test
    void externalWordShouldUsePronunciationRules() {
        double score =
                rankingService.calculateExternalPronunciationScore(
                        List.of("토장"),
                        "통장",
                        true
                );

        assertThat(score).isGreaterThanOrEqualTo(0.85);
    }

    @Test
    void secondPassShouldSupportCompoundPronunciation() {
        double firstScore =
                rankingService.calculateExternalPronunciationScore(
                        List.of("보이더"),
                        "보일러",
                        true
                );

        double secondScore =
                rankingService.calculateExternalPronunciationScore(
                        List.of("보이더"),
                        "보일러",
                        false
                );

        assertThat(secondScore)
                .isGreaterThan(firstScore);
    }

    @Test
    void zeroContextScoreShouldNotBeTreatedAsMissing() {
        double missingContext =
                rankingService.calculateSecondPassScore(
                        0.8, null, null
                );

        double zeroContext =
                rankingService.calculateSecondPassScore(
                        0.8, 0.0, null
                );

        assertThat(missingContext).isEqualTo(0.8);
        assertThat(zeroContext).isLessThan(missingContext);
    }
}

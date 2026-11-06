package com.moyamoya.domain.question.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PronunciationSimilarityTest {

    private final PronunciationSimilarity similarity =
            new PronunciationSimilarity();

    @Test
    void sameWordReturnsOne() {
        assertThat(similarity.calculate("적금", "적금"))
                .isEqualTo(1.0);
    }

    @Test
    void similarPronunciationHasPositiveScore() {
        double score = similarity.calculate("데금", "세금");

        assertThat(score).isGreaterThan(0.0);
        assertThat(score).isLessThan(1.0);
    }

    @Test
    void differentLengthCanBeCompared() {
        double score = similarity.calculate("구케이원", "국회의원");

        assertThat(score).isBetween(0.0, 1.0);
    }

    @Test
    void actualPronunciationCanImproveScore() {
        double withoutVariant =
                similarity.calculate("데금", "세금");

        double withVariant =
                similarity.calculateBest(
                        "데금",
                        "세금",
                        "",
                        "데금"
                );

        assertThat(withVariant).isEqualTo(1.0);
        assertThat(withVariant).isGreaterThan(withoutVariant);
    }

    @Test
    void nullOrBlankReturnsZero() {
        assertThat(similarity.calculate(null, "세금"))
                .isEqualTo(0.0);

        assertThat(similarity.calculate("", "세금"))
                .isEqualTo(0.0);
    }

    @Test
    void scoreAlwaysStaysBetweenZeroAndOne() {
        double score = similarity.calculate("가", "국회의원");

        assertThat(score).isBetween(0.0, 1.0);
    }
}

package com.moyamoya.domain.question.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.question.config.QuestionDataLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PronunciationVariantGeneratorTest {

    private PronunciationVariantGenerator generator;

    @BeforeEach
    void setUp() throws Exception {
        QuestionDataLoader loader =
                new QuestionDataLoader(new ObjectMapper());

        loader.load();
        generator = new PronunciationVariantGenerator(loader);
    }

    @Test
    void initialSubstitution() {
        List<String> variants = generator.generate("세금");

        assertThat(variants).contains("세금", "데금");
    }

    @Test
    void consonantDeletion() {
        List<String> variants = generator.generate("하원");

        assertThat(variants).contains("아원");
    }

    @Test
    void finalConsonantDeletion() {
        List<String> variants = generator.generate("통장");

        assertThat(variants).contains("토장");
    }

    @Test
    void vowelSubstitution() {
        List<String> variants = generator.generate("환기");

        assertThat(variants).contains("한기");
    }

    @Test
    void finalConsonantSubstitution() {
        List<String> variants = generator.generate("선거");

        assertThat(variants).contains("성거");
    }

    @Test
    void medialInitialSubstitution() {
        List<String> variants = generator.generate("경제");

        assertThat(variants).contains("경쩨");
    }

    @Test
    void disabledRuleShouldNotApply() {
        List<String> variants = generator.generate("내비게이션");

        // 규칙 7은 비활성화
        assertThat(variants).doesNotContain("내게이션");
    }

    @Test
    void originalWordShouldAlwaysBeIncluded() {
        List<String> variants = generator.generate("고양이");

        assertThat(variants.get(0)).isEqualTo("고양이");
    }

    @Test
    void reverseInitialSubstitution() {
        assertThat(generator.generateDictionaryQueries("데금", true))
                .contains("데금", "세금");
    }

    @Test
    void reverseFinalConsonantDeletion() {
        assertThat(generator.generateDictionaryQueries("토장", true))
                .contains("토장", "통장");
    }

    @Test
    void reverseVowelSubstitution() {
        assertThat(generator.generateDictionaryQueries("헤식", false))
                .contains("회식");
    }

    @Test
    void reverseVowelShouldFindComputer() {
        assertThat(generator.generateDictionaryQueries("컴퓨타", false))
                .contains("컴퓨터");
    }

    @Test
    void firstPassQueriesShouldBeLimited() {
        assertThat(generator.generateDictionaryQueries("컴퓨타", true))
                .hasSizeLessThanOrEqualTo(10)
                .doesNotHaveDuplicates();
    }

    @Test
    void secondPassQueriesShouldBeLimited() {
        assertThat(generator.generateDictionaryQueries("컴퓨타", false))
                .hasSizeLessThanOrEqualTo(25)
                .doesNotHaveDuplicates();
    }

    @Test
    void originalExpressionShouldBeFirstDictionaryQuery() {
        assertThat(generator.generateDictionaryQueries("토장", true)
                .getFirst()).isEqualTo("토장");
    }

    @Test
    void secondPassShouldExpandDictionaryQueries() {
        List<String> firstQueries =
                generator.generateDictionaryQueries("보이더", true);

        List<String> secondQueries =
                generator.generateDictionaryQueries("보이더", false);

        assertThat(secondQueries)
                .hasSizeGreaterThan(firstQueries.size())
                .hasSizeLessThanOrEqualTo(25)
                .doesNotHaveDuplicates()
                .contains("보이더");
    }
}


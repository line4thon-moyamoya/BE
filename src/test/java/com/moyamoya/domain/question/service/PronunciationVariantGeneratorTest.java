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
}


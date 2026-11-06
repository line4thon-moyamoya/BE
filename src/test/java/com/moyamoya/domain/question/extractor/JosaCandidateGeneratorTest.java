package com.moyamoya.domain.question.extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.question.config.QuestionDataLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JosaCandidateGeneratorTest {

    private JosaCandidateGenerator generator;

    @BeforeEach
    void setUp() throws Exception {
        QuestionDataLoader loader =
                new QuestionDataLoader(new ObjectMapper());

        loader.load();
        generator = new JosaCandidateGenerator(loader);
    }

    @Test
    void 받침이_있는_단어에서_이를_제거한다() {
        assertThat(generator.generate("적금이"))
                .containsExactly("적금이", "적금");
    }

    @Test
    void 받침이_없는_단어에서_가를_제거한다() {
        assertThat(generator.generate("사과가"))
                .containsExactly("사과가", "사과");
    }

    @Test
    void 단어_자체의_이도_원본을_유지한다() {
        assertThat(generator.generate("고양이"))
                .containsExactly("고양이", "고양");
    }

    @Test
    void 원본과_조사제거형을_모두_유지한다() {
        assertThat(generator.generate("길이"))
                .containsExactly("길이", "길");
    }

    @Test
    void 은는_조사를_처리한다() {
        assertThat(generator.generate("학교는"))
                .containsExactly("학교는", "학교");
    }

    @Test
    void 받침_조건에_맞지_않으면_제거하지_않는다() {
        assertThat(generator.generate("적금가"))
                .containsExactly("적금가");
    }

    @Test
    void 조사가_없는_단어는_그대로_유지한다() {
        assertThat(generator.generate("내비게이션"))
                .containsExactly("내비게이션");
    }

    @Test
    void 빈_문자열은_빈_목록을_반환한다() {
        assertThat(generator.generate(" "))
                .isEmpty();
    }
}

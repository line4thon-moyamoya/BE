package com.moyamoya.domain.question.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionDataLoaderTest {

    @Test
    void 질문_데이터를_정상적으로_불러온다() throws Exception {
        QuestionDataLoader loader =
                new QuestionDataLoader(new ObjectMapper());

        loader.load();

        assertThat(loader.getWords()).hasSize(119);
        assertThat(loader.getBadwords()).hasSize(564);
        assertThat(loader.getConfig()).isNotNull();
        assertThat(loader.getPronunciationRules()).isNotNull();
        assertThat(loader.getPronunciationRules()
                .path("rules").size()).isEqualTo(22);
    }
}

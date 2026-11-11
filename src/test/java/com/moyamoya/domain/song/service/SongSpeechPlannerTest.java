package com.moyamoya.domain.song.service;

import static org.assertj.core.api.Assertions.*;

import com.moyamoya.domain.song.model.SongSpeechPlacement;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SongSpeechPlannerTest {
    private final SongSpeechPlanner planner = new SongSpeechPlanner();

    @ParameterizedTest
    @CsvSource(value = {
            "말|말,말|12.0,12.99",
            "적금|적,금,적,금|12.0,12.5,12.99,13.49",
            "소방차|소,방,차|12.0,12.5,12.99",
            "국회의원|국,회,의,원|12.0,12.5,12.99,13.49",
            "예방접종표|예,방,접,종,표|12.0,12.25,12.5,12.74,12.99",
            "어린이도서관|어린이도서관|12.0"
    }, delimiter = '|')
    void appliesLengthSpecificSyllableRules(String word, String spoken, String starts) {
        var result = planner.plan(List.of(word, "가나다라마바", "가나다라마바", "가나다라마바", "가나다라마바"));
        var first = result.stream().filter(placement -> placement.startSec() < 16).toList();

        assertThat(first).extracting(SongSpeechPlacement::text).containsExactly(spoken.split(","));
        assertThat(first).extracting(SongSpeechPlacement::startSec)
                .containsExactlyElementsOf(Arrays.stream(starts.split(",")).map(Double::valueOf).toList());
    }

    @Test
    void usesFiveFixedStartsAndStripsSurroundingWhitespace() {
        var result = planner.plan(List.of(" 어린이도서관 ", "어린이박물관", "가나다라마바", "초등학교입학", "즐거운하루야"));

        assertThat(result).extracting(SongSpeechPlacement::startSec).containsExactly(12.0, 16.0, 20.0, 24.0, 28.0);
        assertThat(result.getFirst().text()).isEqualTo("어린이도서관");
    }

    @Test
    void rejectsMissingOrInvalidWords() {
        assertThatIllegalArgumentException().isThrownBy(() -> planner.plan(null));
        assertThatIllegalArgumentException().isThrownBy(() -> planner.plan(List.of("적금")));
        for (String invalid : Arrays.asList(null, "", "  ", "가".repeat(101))) {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> planner.plan(Arrays.asList("가", "나", "다", "라", invalid)));
        }
    }
}

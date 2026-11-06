package com.moyamoya.domain.word.util;

import com.moyamoya.domain.word.entity.HeardFrom;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeardFromMapperTest {

    @Test
    void fromCsvMapsKnownValues() {
        assertThat(HeardFromMapper.fromCsv("parent")).isEqualTo(HeardFrom.PARENTS);
        assertThat(HeardFromMapper.fromCsv("kindergarten")).isEqualTo(HeardFrom.KINDERGARTEN);
        assertThat(HeardFromMapper.fromCsv("book")).isEqualTo(HeardFrom.BOOK);
        assertThat(HeardFromMapper.fromCsv("tv")).isEqualTo(HeardFrom.TV_YOUTUBE);
        assertThat(HeardFromMapper.fromCsv("")).isEqualTo(HeardFrom.UNKNOWN);
    }

    @Test
    void fromCsvThrowsForUnsupportedValue() {
        assertThatThrownBy(() -> HeardFromMapper.fromCsv("school"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void matchesReturnsTrueOnlyForExactEnumMatch() {
        assertThat(HeardFromMapper.matches(HeardFrom.PARENTS, "parent")).isTrue();
        assertThat(HeardFromMapper.matches(HeardFrom.PARENTS, "tv")).isFalse();
        assertThat(HeardFromMapper.matches(HeardFrom.UNKNOWN, "parent")).isFalse();
        assertThat(HeardFromMapper.matches(null, "parent")).isFalse();
    }
}

package com.moyamoya.domain.report.entity;

import com.moyamoya.global.exception.CustomException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeeklyReportTest {

    @Test
    void createNormalizesDateToMondayOfSameWeek() {
        WeeklyReport report = WeeklyReport.create(null, LocalDate.of(2026, 11, 4), "{\"summary\":\"ok\"}");

        assertThat(report.getWeekStartDate()).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void createKeepsMondayAsIs() {
        WeeklyReport report = WeeklyReport.create(null, LocalDate.of(2026, 11, 2), "{\"summary\":\"ok\"}");

        assertThat(report.getWeekStartDate()).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void createRejectsNullWeekStartDate() {
        assertThatThrownBy(() -> WeeklyReport.create(null, null, "{\"summary\":\"ok\"}"))
                .isInstanceOf(CustomException.class)
                .hasMessage("올바르지 않은 주차입니다.");
    }
}

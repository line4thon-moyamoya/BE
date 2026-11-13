package com.moyamoya.domain.report.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record WeeklyReportPreviewRequest(
        @NotNull Long childId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {
}

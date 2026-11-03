package com.moyamoya.domain.report.entity;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.report.exception.WeeklyReportErrorCode;
import com.moyamoya.global.exception.CustomException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

@Entity
@Table(
        name = "weekly_reports",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_weekly_reports_child_week",
                        columnNames = {"child_id", "week_start_date"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    private Child child;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report_data", nullable = false, columnDefinition = "json")
    private String reportData;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static WeeklyReport create(
            Child child,
            LocalDate weekStartDate,
            String reportData
    ) {
        WeeklyReport report = new WeeklyReport();
        report.child = child;
        report.weekStartDate = normalizeWeekStartDate(weekStartDate);
        report.reportData = reportData;
        return report;
    }

    private static LocalDate normalizeWeekStartDate(LocalDate date) {
        if (date == null) {
            throw new CustomException(WeeklyReportErrorCode.REPORT_INVALID_WEEK);
        }
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}

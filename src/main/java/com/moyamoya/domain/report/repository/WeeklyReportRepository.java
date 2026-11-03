package com.moyamoya.domain.report.repository;

import com.moyamoya.domain.report.entity.WeeklyReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeeklyReportRepository
        extends JpaRepository<WeeklyReport, Long> {

    Optional<WeeklyReport> findByChildIdAndWeekStartDate(
            Long childId,
            LocalDate weekStartDate
    );

    List<WeeklyReport> findByChildIdOrderByWeekStartDateDesc(
            Long childId
    );

    boolean existsByChildIdAndWeekStartDate(
            Long childId,
            LocalDate weekStartDate
    );
}

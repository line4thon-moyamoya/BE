package com.moyamoya.domain.report.service;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyReportScheduler {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");

    private final ChildRepository childRepository;
    private final WeeklyReportService weeklyReportService;

    @Scheduled(cron = "0 10 0 * * MON", zone = "Asia/Seoul")
    public void generateLastWeekReports() {

        LocalDateTime snapshotAt = LocalDateTime.now(APP_ZONE);

        LocalDate thisWeekStart = snapshotAt.toLocalDate().with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );
        LocalDate targetWeekStart = thisWeekStart.minusWeeks(1);

        List<Child> children = childRepository.findAll();

        int successCount = 0;
        int failedCount = 0;

        for (Child child : children) {
            try {
                weeklyReportService.generate(
                        child,
                        targetWeekStart,
                        snapshotAt
                );
                successCount++;

            } catch (Exception e) {
                failedCount++;

                log.error(
                        "주간 리포트 생성 실패 - childId={}, weekStartDate={}",
                        child.getId(),
                        targetWeekStart,
                        e
                );
            }
        }

        log.info(
                "주간 리포트 생성 완료 - weekStartDate={}, success={}, failed={}",
                targetWeekStart,
                successCount,
                failedCount
        );
    }
}

package com.moyamoya.domain.report.service;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeeklyReportSchedulerTest {

    @Mock
    ChildRepository childRepository;

    @Mock
    WeeklyReportService weeklyReportService;

    @InjectMocks
    WeeklyReportScheduler scheduler;

    @Test
    void generatesReportsForAllChildren() {
        Child child1 = mock(Child.class);
        Child child2 = mock(Child.class);
        Child child3 = mock(Child.class);

        when(childRepository.findAll())
                .thenReturn(List.of(child1, child2, child3));

        scheduler.generateLastWeekReports();

        ArgumentCaptor<LocalDate> dateCaptor =
                ArgumentCaptor.forClass(LocalDate.class);

        ArgumentCaptor<LocalDateTime> snapshotCaptor =
                ArgumentCaptor.forClass(LocalDateTime.class);

        verify(weeklyReportService, times(3)).generate(
                any(Child.class),
                dateCaptor.capture(),
                snapshotCaptor.capture()
        );

        List<LocalDate> dates = dateCaptor.getAllValues();
        List<LocalDateTime> snapshots = snapshotCaptor.getAllValues();

        // 스케줄러에서 사용한 스냅샷 시각을 기준으로 날짜 계산
        LocalDate expectedWeekStart = snapshots.get(0)
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(1);

        // 모든 아이에게 동일한 주차와 스냅샷 시각 전달
        assertThat(dates)
                .containsOnly(expectedWeekStart);

        assertThat(snapshots)
                .containsOnly(snapshots.get(0));

        verify(weeklyReportService).generate(
                eq(child1),
                eq(expectedWeekStart),
                eq(snapshots.get(0))
        );

        verify(weeklyReportService).generate(
                eq(child2),
                eq(expectedWeekStart),
                eq(snapshots.get(0))
        );

        verify(weeklyReportService).generate(
                eq(child3),
                eq(expectedWeekStart),
                eq(snapshots.get(0))
        );
    }

    @Test
    void continuesGeneratingWhenOneChildFails() {
        Child child1 = mock(Child.class);
        Child child2 = mock(Child.class);
        Child child3 = mock(Child.class);

        when(child1.getId()).thenReturn(1L);

        when(childRepository.findAll())
                .thenReturn(List.of(child1, child2, child3));

        doThrow(new RuntimeException("DB 오류"))
                .when(weeklyReportService)
                .generate(
                        eq(child1),
                        any(LocalDate.class),
                        any(LocalDateTime.class)
                );

        scheduler.generateLastWeekReports();

        // 첫 번째 아이가 실패해도 나머지 아이는 처리
        verify(weeklyReportService).generate(
                eq(child2),
                any(LocalDate.class),
                any(LocalDateTime.class)
        );

        verify(weeklyReportService).generate(
                eq(child3),
                any(LocalDate.class),
                any(LocalDateTime.class)
        );
    }

    @Test
    void doesNotGenerateWhenNoChildrenExist() {
        when(childRepository.findAll())
                .thenReturn(List.of());

        scheduler.generateLastWeekReports();

        verifyNoInteractions(weeklyReportService);
    }
}


package com.moyamoya.domain.report.service;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

        // 아이 3명 모두 생성 호출
        verify(weeklyReportService, times(3))
                .generate(
                        any(Child.class),
                        any(LocalDate.class),
                        any(LocalDateTime.class)
                );

        // 지난주 월요일 날짜가 전달되었는지 확인
        LocalDate expectedWeekStart = LocalDate.now()
                .with(DayOfWeek.MONDAY)
                .minusWeeks(1);

        verify(weeklyReportService).generate(
                eq(child1),
                eq(expectedWeekStart),
                any(LocalDateTime.class)
        );

        verify(weeklyReportService).generate(
                eq(child2),
                eq(expectedWeekStart),
                any(LocalDateTime.class)
        );

        verify(weeklyReportService).generate(
                eq(child3),
                eq(expectedWeekStart),
                any(LocalDateTime.class)
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


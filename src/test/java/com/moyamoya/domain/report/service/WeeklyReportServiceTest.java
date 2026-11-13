package com.moyamoya.domain.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.report.dto.WeeklyReportSnapshot;
import com.moyamoya.domain.report.dto.response.WeeklyReportResponse.LearnedWord;
import com.moyamoya.domain.report.entity.WeeklyReport;
import com.moyamoya.domain.report.repository.WeeklyReportRepository;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.s3.S3Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeeklyReportServiceTest {

    @Mock
    WeeklyReportRepository weeklyReportRepository;

    @Mock
    WeeklyReportAggregationService aggregationService;

    @Mock
    WeeklyReportConversationTopicService conversationTopicService;

    @Mock
    ChildRepository childRepository;

    @Mock
    S3Service s3Service;

    WeeklyReportService service;

    private final LocalDate weekStartDate =
            LocalDate.of(2026, 11, 9);

    private final LocalDateTime snapshotAt =
            LocalDateTime.of(2026, 11, 16, 0, 10);

    @BeforeEach
    void setUp() {
        service = new WeeklyReportService(
                weeklyReportRepository,
                aggregationService,
                conversationTopicService,
                childRepository,
                new ObjectMapper(),
                s3Service
        );
    }

    @Test
    void generatesAndSavesWeeklyReport() throws Exception {
        Child child = mock(Child.class);
        when(child.getId()).thenReturn(1L);

        WeeklyReportSnapshot snapshot = new WeeklyReportSnapshot(
                2,
                0,
                List.of(),
                List.of(
                        new LearnedWord(
                                10L,
                                "적금",
                                List.of("저금", "저끔"),
                                "images/10.png"
                        )
                ),
                null
        );

        when(weeklyReportRepository.existsByChildIdAndWeekStartDate(
                1L, weekStartDate
        )).thenReturn(false);

        when(aggregationService.aggregate(
                1L, weekStartDate, snapshotAt
        )).thenReturn(snapshot);

        when(conversationTopicService.generate(
                eq(child), anyList(), eq(0)
        )).thenReturn("적금으로 무엇을 할 수 있을까? 하고 물어봐 주세요.");

        service.generate(child, weekStartDate, snapshotAt);

        ArgumentCaptor<WeeklyReport> captor =
                ArgumentCaptor.forClass(WeeklyReport.class);

        verify(weeklyReportRepository).save(captor.capture());

        WeeklyReport savedReport = captor.getValue();

        assertThat(savedReport.getChild()).isSameAs(child);
        assertThat(savedReport.getWeekStartDate())
                .isEqualTo(weekStartDate);

        WeeklyReportSnapshot savedSnapshot =
                new ObjectMapper().readValue(
                        savedReport.getReportData(),
                        WeeklyReportSnapshot.class
                );

        assertThat(savedSnapshot.questionCount()).isEqualTo(2);
        assertThat(savedSnapshot.unrecognizedCount()).isZero();
        assertThat(savedSnapshot.words()).hasSize(1);
        assertThat(savedSnapshot.words().get(0).word())
                .isEqualTo("적금");

        // Presigned URL이 아닌 S3 Key 저장
        assertThat(savedSnapshot.words().get(0).imageUrl())
                .isEqualTo("images/10.png");

        assertThat(savedSnapshot.conversationTopic())
                .isEqualTo("적금으로 무엇을 할 수 있을까? 하고 물어봐 주세요.");

        verifyNoInteractions(s3Service);
    }

    @Test
    void skipsWhenReportAlreadyExists() {
        Child child = mock(Child.class);
        when(child.getId()).thenReturn(1L);

        when(weeklyReportRepository.existsByChildIdAndWeekStartDate(
                1L, weekStartDate
        )).thenReturn(true);

        service.generate(child, weekStartDate, snapshotAt);

        verify(weeklyReportRepository, never())
                .save(any(WeeklyReport.class));

        verifyNoInteractions(
                aggregationService,
                conversationTopicService,
                s3Service
        );
    }

    @Test
    void invalidWeekStartDate_throwsException() {
        Child child = mock(Child.class);

        LocalDate invalidDate =
                LocalDate.of(2026, 11, 10);

        assertThatThrownBy(() ->
                service.generate(child, invalidDate, snapshotAt)
        ).isInstanceOf(CustomException.class);

        verifyNoInteractions(weeklyReportRepository);
    }

    @Test
    void nullSnapshotAt_throwsException() {
        Child child = mock(Child.class);

        assertThatThrownBy(() ->
                service.generate(child, weekStartDate, null)
        ).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(weeklyReportRepository);
    }
}


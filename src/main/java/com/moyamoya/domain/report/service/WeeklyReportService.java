package com.moyamoya.domain.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.exception.ChildErrorCode;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.guardian.exception.GuardianErrorCode;
import com.moyamoya.domain.report.dto.WeeklyReportSnapshot;
import com.moyamoya.domain.report.dto.response.WeeklyReportPreviewResponse;
import com.moyamoya.domain.report.dto.response.WeeklyReportResponse;
import com.moyamoya.domain.report.dto.response.WeeklyReportResponse.LearnedWord;
import com.moyamoya.domain.report.entity.WeeklyReport;
import com.moyamoya.domain.report.exception.WeeklyReportErrorCode;
import com.moyamoya.domain.report.repository.WeeklyReportRepository;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.s3.S3Service;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class WeeklyReportService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");

    private final WeeklyReportRepository weeklyReportRepository;
    private final WeeklyReportAggregationService aggregationService;
    private final WeeklyReportConversationTopicService conversationTopicService;
    private final ChildRepository childRepository;
    private final ObjectMapper objectMapper;
    private final S3Service s3Service;

    @Transactional
    public void generate(
            Child child,
            LocalDate weekStartDate,
            LocalDateTime snapshotAt
    ) {
        validateWeekStartDate(weekStartDate);
        validateSnapshotAt(snapshotAt);

        if (weeklyReportRepository.existsByChildIdAndWeekStartDate(
                child.getId(),
                weekStartDate
        )) {
            return;
        }

        WeeklyReportSnapshot snapshot = aggregationService.aggregate(
                child.getId(),
                weekStartDate,
                snapshotAt
        );
        WeeklyReportSnapshot snapshotWithTopic = applyConversationTopic(
                child,
                snapshot
        );

        String reportData = serialize(snapshotWithTopic);

        WeeklyReport report = WeeklyReport.create(
                child,
                weekStartDate,
                reportData
        );

        weeklyReportRepository.save(report);
    }

    @Transactional(readOnly = true)
    public WeeklyReportResponse getReport(
            Child child,
            LocalDate weekStartDate
    ) {
        validateWeekStartDate(weekStartDate);

        WeeklyReport report = weeklyReportRepository
                .findByChildIdAndWeekStartDate(
                        child.getId(),
                        weekStartDate
                )
                .orElseThrow(() -> new CustomException(
                        WeeklyReportErrorCode.REPORT_NOT_FOUND
                ));

        WeeklyReportSnapshot snapshot =
                deserialize(report.getReportData());

        WeeklyReportSnapshot resolvedSnapshot = resolveSnapshotImageUrls(snapshot);

        return new WeeklyReportResponse(
                report.getId(),
                child.getId(),
                child.getNickname(),
                report.getWeekStartDate(),
                report.getWeekStartDate().plusDays(6),
                resolvedSnapshot.questionCount(),
                resolvedSnapshot.unrecognizedCount(),
                resolvedSnapshot.unrecognizedWords(),
                resolvedSnapshot.words(),
                resolvedSnapshot.conversationTopic(),
                report.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public WeeklyReportResponse getReportForGuardian(
            Long userId,
            Long childId,
            LocalDate weekStartDate
    ) {
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new CustomException(
                        ChildErrorCode.CHILD_NOT_FOUND
                ));

        if (!child.getUser().getId().equals(userId)) {
            throw new CustomException(GuardianErrorCode.ACCESS_FORBIDDEN);
        }

        return getReport(child, weekStartDate);
    }

    @Transactional(readOnly = true)
    public WeeklyReportPreviewResponse previewForGuardian(
            Long userId,
            Long childId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (startDate == null
                || endDate == null
                || endDate.isBefore(startDate)) {
            throw new CustomException(
                    WeeklyReportErrorCode.REPORT_INVALID_RANGE
            );
        }

        if (ChronoUnit.DAYS.between(startDate, endDate) > 30) {
            throw new CustomException(
                    WeeklyReportErrorCode.REPORT_INVALID_RANGE
            );
        }

        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new CustomException(
                        ChildErrorCode.CHILD_NOT_FOUND
                ));

        if (!child.getUser().getId().equals(userId)) {
            throw new CustomException(GuardianErrorCode.ACCESS_FORBIDDEN);
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();
        LocalDateTime snapshotAt = LocalDateTime.now(APP_ZONE);

        WeeklyReportSnapshot snapshot = aggregationService.aggregateRange(
                childId,
                start,
                endExclusive,
                snapshotAt
        );
        WeeklyReportSnapshot snapshotWithTopic = applyConversationTopic(
                child,
                snapshot
        );
        WeeklyReportSnapshot resolvedSnapshot =
                resolveSnapshotImageUrls(snapshotWithTopic);

        return new WeeklyReportPreviewResponse(
                child.getId(),
                child.getNickname(),
                startDate,
                endDate,
                resolvedSnapshot.questionCount(),
                resolvedSnapshot.unrecognizedCount(),
                resolvedSnapshot.unrecognizedWords(),
                resolvedSnapshot.words(),
                resolvedSnapshot.conversationTopic(),
                snapshotAt
        );
    }

    private String resolveImageUrl(String s3Key) {
        if (s3Key == null || s3Key.isBlank()) {
            return null;
        }
        return s3Service.getPresignedUrl(s3Key);
    }

    private WeeklyReportSnapshot applyConversationTopic(
            Child child,
            WeeklyReportSnapshot snapshot
    ) {
        String topic = conversationTopicService.generate(
                child,
                snapshot.words(),
                snapshot.unrecognizedCount()
        );

        return new WeeklyReportSnapshot(
                snapshot.questionCount(),
                snapshot.unrecognizedCount(),
                snapshot.unrecognizedWords(),
                snapshot.words(),
                topic
        );
    }

    private WeeklyReportSnapshot resolveSnapshotImageUrls(
            WeeklyReportSnapshot snapshot
    ) {
        List<LearnedWord> words = snapshot.words().stream()
                .map(word -> new LearnedWord(
                        word.wordId(),
                        word.word(),
                        word.recognizedTexts(),
                        resolveImageUrl(word.imageUrl())
                ))
                .toList();

        return new WeeklyReportSnapshot(
                snapshot.questionCount(),
                snapshot.unrecognizedCount(),
                snapshot.unrecognizedWords(),
                words,
                snapshot.conversationTopic()
        );
    }

    private String serialize(WeeklyReportSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.error("주간 리포트 JSON 직렬화 실패", e);
            throw new CustomException(
                    WeeklyReportErrorCode.REPORT_GENERATION_FAILED
            );
        }
    }

    private WeeklyReportSnapshot deserialize(String json) {
        try {
            return objectMapper.readValue(
                    json,
                    WeeklyReportSnapshot.class
            );
        } catch (JsonProcessingException e) {
            log.error("주간 리포트 JSON 역직렬화 실패", e);
            throw new CustomException(
                    WeeklyReportErrorCode.REPORT_GENERATION_FAILED
            );
        }
    }

    private void validateWeekStartDate(LocalDate weekStartDate) {
        if (weekStartDate == null
                || weekStartDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new CustomException(
                    WeeklyReportErrorCode.REPORT_INVALID_WEEK
            );
        }
    }

    private void validateSnapshotAt(LocalDateTime snapshotAt) {
        if (snapshotAt == null) {
            throw new IllegalArgumentException(
                    "snapshotAt은 null일 수 없습니다."
            );
        }
    }
}

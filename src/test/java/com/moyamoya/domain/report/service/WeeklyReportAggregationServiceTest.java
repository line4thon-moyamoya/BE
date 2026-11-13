package com.moyamoya.domain.report.service;

import com.moyamoya.domain.report.dto.WeeklyReportSnapshot;
import com.moyamoya.domain.word.entity.QuestionHistory;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.entity.WordRequest;
import com.moyamoya.domain.word.entity.WordRequestStatus;
import com.moyamoya.domain.word.repository.QuestionHistoryRepository;
import com.moyamoya.domain.word.repository.WordRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeeklyReportAggregationServiceTest {
    @Mock QuestionHistoryRepository historyRepository;
    @Mock WordRequestRepository requestRepository;
    WeeklyReportAggregationService service;
    private final LocalDateTime snapshotAt = LocalDateTime.of(2026, 11, 16, 0, 10);

    @BeforeEach
    void setUp() {
        service = new WeeklyReportAggregationService(historyRepository, requestRepository);
    }

    @Test
    void confirmedResolvedPending_areClassifiedCorrectly() {
        Word word = mock(Word.class);
        when(word.getId()).thenReturn(10L);
        when(word.getWord()).thenReturn("적금");
        when(word.getImageUrl()).thenReturn("images/10.png");
        QuestionHistory h1 = history(word, "저금");
        QuestionHistory h2 = history(word, "저끔");
        WordRequest resolved = request(1L, "극정감자", "극장", WordRequestStatus.RESOLVED,
                snapshotAt.minusDays(1));
        WordRequest pending = request(2L, "통장이", null, WordRequestStatus.PENDING, null);
        stub(List.of(h1, h2), List.of(resolved, pending));

        WeeklyReportSnapshot result = service.aggregate(1L, LocalDate.of(2026, 11, 9), snapshotAt);

        assertThat(result.questionCount()).isEqualTo(4);
        assertThat(result.unrecognizedCount()).isEqualTo(1);
        assertThat(result.unrecognizedWords()).extracting("recognizedText").containsExactly("통장이");
        assertThat(result.words()).hasSize(2);
        assertThat(result.words().get(0).wordId()).isEqualTo(10L);
        assertThat(result.words().get(0).recognizedTexts()).containsExactly("저금", "저끔");
        assertThat(result.words().get(1).wordId()).isNull();
        assertThat(result.words().get(1).word()).isEqualTo("극장");
        assertThat(result.words().get(1).recognizedTexts()).containsExactly("극정감자");
    }

    @Test
    void resolvedAfterSnapshot_isStillUnrecognized() {
        WordRequest future = request(3L, "꼬끼오", "닭", WordRequestStatus.RESOLVED,
                snapshotAt.plusMinutes(1));
        stub(List.of(), List.of(future));
        WeeklyReportSnapshot result = service.aggregate(1L, LocalDate.of(2026, 11, 9), snapshotAt);
        assertThat(result.questionCount()).isEqualTo(1);
        assertThat(result.unrecognizedCount()).isEqualTo(1);
        assertThat(result.words()).isEmpty();
    }

    @Test
    void resolvedWithoutCorrectedWord_skipsInvalidWord() {
        WordRequest invalid = request(
                4L,
                "극정감자",
                "  ",
                WordRequestStatus.RESOLVED,
                snapshotAt.minusDays(1)
        );

        stub(List.of(), List.of(invalid));

        WeeklyReportSnapshot result = service.aggregate(
                1L,
                LocalDate.of(2026, 11, 9),
                snapshotAt
        );

        assertThat(result.questionCount()).isEqualTo(1);

        assertThat(result.words()).isEmpty();

        assertThat(result.unrecognizedCount()).isZero();
        assertThat(result.unrecognizedWords()).isEmpty();
    }

    @Test
    void resolvedWithoutResolvedAt_isStillUnrecognized() {
        WordRequest request = request(
                5L,
                "통장이",
                "통장",
                WordRequestStatus.RESOLVED,
                null
        );

        stub(List.of(), List.of(request));

        WeeklyReportSnapshot result = service.aggregate(
                1L,
                LocalDate.of(2026, 11, 9),
                snapshotAt
        );

        assertThat(result.questionCount()).isEqualTo(1);
        assertThat(result.unrecognizedCount()).isEqualTo(1);
        assertThat(result.words()).isEmpty();
        assertThat(result.unrecognizedWords())
                .extracting("recognizedText")
                .containsExactly("통장이");
    }

    @Test
    void noQuestions_returnsEmptyListsAndZeroCounts() {
        stub(List.of(), List.of());
        WeeklyReportSnapshot result = service.aggregate(1L, LocalDate.of(2026, 11, 9), snapshotAt);
        assertThat(result.questionCount()).isZero();
        assertThat(result.unrecognizedCount()).isZero();
        assertThat(result.words()).isEmpty();
        assertThat(result.unrecognizedWords()).isEmpty();
    }

    private QuestionHistory history(Word word, String recognizedText) {
        QuestionHistory history = mock(QuestionHistory.class);
        when(history.getWord()).thenReturn(word);
        when(history.getRecognizedText()).thenReturn(recognizedText);
        return history;
    }

    private WordRequest request(Long id, String recognizedText, String correctedWord,
                                WordRequestStatus status, LocalDateTime resolvedAt) {
        WordRequest request = mock(WordRequest.class);
        lenient().when(request.getId()).thenReturn(id);
        lenient().when(request.getRecognizedText()).thenReturn(recognizedText);
        lenient().when(request.getCorrectedWord()).thenReturn(correctedWord);
        when(request.getStatus()).thenReturn(status);
        lenient().when(request.getResolvedAt()).thenReturn(resolvedAt);
        return request;
    }

    private void stub(List<QuestionHistory> histories, List<WordRequest> requests) {
        when(historyRepository.findByChildIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                eq(1L), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(histories);
        when(requestRepository.findByChildIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                eq(1L), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(requests);
    }
}

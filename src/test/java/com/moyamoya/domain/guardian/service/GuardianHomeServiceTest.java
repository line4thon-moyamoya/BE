package com.moyamoya.domain.guardian.service;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.guardian.dto.response.GuardianHomeResponse;
import com.moyamoya.domain.guardian.exception.GuardianErrorCode;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.word.entity.WordRequest;
import com.moyamoya.domain.word.entity.WordRequestStatus;
import com.moyamoya.domain.word.repository.QuestionHistoryRepository;
import com.moyamoya.domain.word.repository.WordRequestRepository;
import com.moyamoya.global.exception.CustomException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GuardianHomeServiceTest {

    @Mock
    private ChildRepository childRepository;

    @Mock
    private QuestionHistoryRepository questionHistoryRepository;

    @Mock
    private WordRequestRepository wordRequestRepository;

    @InjectMocks
    private GuardianHomeService guardianHomeService;

    private User user;
    private Child child;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
        child = mock(Child.class);
    }

    private void stubChild() {
        when(child.getId()).thenReturn(1L);
        when(child.getNickname()).thenReturn("지우");
    }

    @Test
    void getHomeSelectsFirstChildAndCalculatesWeeklyStats() {
        stubChild();

        when(childRepository.findByUserIdOrderByIdAsc(1L))
                .thenReturn(List.of(child));

        // 질문 기록 8건 + 물어볼 단어 4건 = 12건
        when(questionHistoryRepository
                .countByChildIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        eq(1L), any(), any()
                ))
                .thenReturn(8L);

        when(wordRequestRepository
                .countByChildIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        eq(1L), any(), any()
                ))
                .thenReturn(4L);

        // 이번 주 미해결 3건
        when(wordRequestRepository
                .countByChildIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        eq(1L),
                        eq(WordRequestStatus.PENDING),
                        any(),
                        any()
                ))
                .thenReturn(3L);

        // 전체 기간 미해결 5건
        when(wordRequestRepository.countByChildIdAndStatus(
                1L, WordRequestStatus.PENDING
        )).thenReturn(5L);

        WordRequest request = mock(WordRequest.class);
        when(request.getId()).thenReturn(31L);
        when(request.getRecognizedText()).thenReturn("극정감자");

        when(wordRequestRepository
                .findTop3ByChildIdAndStatusOrderByCreatedAtDesc(
                        1L, WordRequestStatus.PENDING
                ))
                .thenReturn(List.of(request));

        GuardianHomeResponse result =
                guardianHomeService.getHome(1L, null);

        assertEquals(1L, result.selectedChildId());
        assertEquals("지우", result.childName());
        assertEquals(12L, result.questionCount());
        assertEquals(3L, result.unrecognizedCount());
        assertEquals(5L, result.pendingRequestCount());
        assertEquals(1, result.pendingRequestPreview().size());
        assertEquals("극정감자",
                result.pendingRequestPreview().get(0).recognizedText());

        verify(childRepository).findByUserIdOrderByIdAsc(1L);
        verify(childRepository, never()).findById(anyLong());
    }

    @Test
    void getHomeUsesMondayToNextMondayDateRange() {
        stubChild();

        when(childRepository.findByUserIdOrderByIdAsc(1L))
                .thenReturn(List.of(child));

        GuardianHomeResponse result =
                guardianHomeService.getHome(1L, null);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate monday = today.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );

        LocalDateTime expectedStart = monday.atStartOfDay();
        LocalDateTime expectedEnd =
                monday.plusWeeks(1).atStartOfDay();

        assertEquals(monday, result.weekStartDate());
        assertEquals(monday.plusDays(6), result.weekEndDate());

        verify(questionHistoryRepository)
                .countByChildIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        1L, expectedStart, expectedEnd
                );

        verify(wordRequestRepository)
                .countByChildIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        1L,
                        WordRequestStatus.PENDING,
                        expectedStart,
                        expectedEnd
                );
    }

    @Test
    void getHomeReturnsEmptyWhenNoChildren() {
        when(childRepository.findByUserIdOrderByIdAsc(1L))
                .thenReturn(List.of());

        GuardianHomeResponse result =
                guardianHomeService.getHome(1L, null);

        assertTrue(result.children().isEmpty());
        assertNull(result.selectedChildId());
        assertEquals(0L, result.questionCount());
        assertTrue(result.pendingRequestPreview().isEmpty());

        verifyNoInteractions(
                questionHistoryRepository,
                wordRequestRepository
        );
    }

    @Test
    void getHomeAllowsSelectingOwnedChild() {
        Child secondChild = mock(Child.class);

        when(childRepository.findByUserIdOrderByIdAsc(1L))
                .thenReturn(List.of(child, secondChild));

        when(childRepository.findById(2L))
                .thenReturn(Optional.of(secondChild));

        when(child.getId()).thenReturn(1L);
        when(child.getNickname()).thenReturn("지우");

        when(secondChild.getId()).thenReturn(2L);
        when(secondChild.getNickname()).thenReturn("서아");
        when(secondChild.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);

        GuardianHomeResponse result =
                guardianHomeService.getHome(1L, 2L);

        assertEquals(2L, result.selectedChildId());
        assertEquals("서아", result.childName());
        assertEquals(2, result.children().size());
    }

    @Test
    void getHomeRejectsOtherUsersChild() {
        User otherUser = mock(User.class);
        Child otherChild = mock(Child.class);

        when(childRepository.findByUserIdOrderByIdAsc(1L))
                .thenReturn(List.of(child));

        when(childRepository.findById(99L))
                .thenReturn(Optional.of(otherChild));

        when(otherChild.getUser()).thenReturn(otherUser);
        when(otherUser.getId()).thenReturn(2L);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> guardianHomeService.getHome(1L, 99L)
        );

        assertEquals(
                GuardianErrorCode.ACCESS_FORBIDDEN,
                exception.getErrorCode()
        );

        verifyNoInteractions(
                questionHistoryRepository,
                wordRequestRepository
        );
    }

    @Test
    void getHomeDoesNotIncludeResolvedRequestsInPendingCount() {
        stubChild();

        when(childRepository.findByUserIdOrderByIdAsc(1L))
                .thenReturn(List.of(child));

        // 이번 주 요청 4건 (해결 2건 + 미해결 2건)
        when(wordRequestRepository
                .countByChildIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        eq(1L), any(), any()
                ))
                .thenReturn(4L);

        when(wordRequestRepository
                .countByChildIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        eq(1L),
                        eq(WordRequestStatus.PENDING),
                        any(),
                        any()
                ))
                .thenReturn(2L);

        GuardianHomeResponse result =
                guardianHomeService.getHome(1L, null);

        assertEquals(4L, result.questionCount());
        assertEquals(2L, result.unrecognizedCount());
    }
}


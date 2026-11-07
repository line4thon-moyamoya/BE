package com.moyamoya.domain.guardian.service;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.exception.ChildErrorCode;
import com.moyamoya.domain.guardian.exception.GuardianErrorCode;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.guardian.dto.response.GuardianHomeResponse;
import com.moyamoya.domain.guardian.dto.response.UpdateSelectedChildResponse;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.user.repository.UserRepository;
import com.moyamoya.domain.word.entity.WordRequestStatus;
import com.moyamoya.domain.word.repository.QuestionHistoryRepository;
import com.moyamoya.domain.word.repository.WordRequestRepository;
import com.moyamoya.global.exception.CustomException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GuardianHomeService {

    private static final ZoneId KOREA_ZONE =
            ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final ChildRepository childRepository;
    private final QuestionHistoryRepository questionHistoryRepository;
    private final WordRequestRepository wordRequestRepository;

    @Transactional(readOnly = true)
    public GuardianHomeResponse getHome(Long userId, Long childId) {

        // 로그인한 보호자의 아이 목록 조회
        List<Child> children =
                childRepository.findByUserIdOrderByIdAsc(userId);

        // 아이가 없으면 빈 홈 응답
        if (children.isEmpty() && childId == null) {
            return GuardianHomeResponse.empty();
        }

        // 선택된 아이 결정 및 소유권 검증
        Child selectedChild;

        if (childId == null) {
            // 최초 진입: 첫 번째 아이 자동 선택
            selectedChild = children.get(0);
        } else {
            // 명시된 아이의 존재 여부 확인
            selectedChild = childRepository.findById(childId)
                    .orElseThrow(() ->
                            new CustomException(ChildErrorCode.CHILD_NOT_FOUND)
                    );

            // 다른 보호자의 아이인지 확인
            if (userId == null ||
                    !selectedChild.getUser().getId().equals(userId)) {
                throw new CustomException(GuardianErrorCode.ACCESS_FORBIDDEN);
            }
        }

        Long selectedChildId = selectedChild.getId();

        // 이번 주 월요일 ~ 일요일 계산
        LocalDate today = LocalDate.now(KOREA_ZONE);

        LocalDate weekStart = today.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );

        LocalDate weekEnd = weekStart.plusDays(6);

        // 조회 구간: 월요일 00:00 이상 ~ 다음 월요일 00:00 미만
        LocalDateTime start = weekStart.atStartOfDay();
        LocalDateTime endExclusive =
                weekStart.plusWeeks(1).atStartOfDay();

        // 이번 주 정상 질문 기록 (중복 질문 포함)
        long historyCount = questionHistoryRepository
                .countByChildIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        selectedChildId,
                        start,
                        endExclusive
                );

        // 이번 주 물어볼 단어 전체 (해결/미해결 포함)
        long requestCount = wordRequestRepository
                .countByChildIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        selectedChildId,
                        start,
                        endExclusive
                );

        // 이번 주 물어본 단어 전체
        long questionCount = historyCount + requestCount;

        // 이번 주 못 찾은 말 (현재 PENDING)
        long unrecognizedCount = wordRequestRepository
                .countByChildIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        selectedChildId,
                        WordRequestStatus.PENDING,
                        start,
                        endExclusive
                );

        // 전체 기간 미해결 요청 수
        long pendingRequestCount = wordRequestRepository
                .countByChildIdAndStatus(
                        selectedChildId,
                        WordRequestStatus.PENDING
                );

        // 전체 기간 미해결 요청 최신 3개
        List<GuardianHomeResponse.PendingRequestPreview> previews =
                wordRequestRepository
                        .findTop3ByChildIdAndStatusOrderByCreatedAtDesc(
                                selectedChildId,
                                WordRequestStatus.PENDING
                        )
                        .stream()
                        .map(GuardianHomeResponse.PendingRequestPreview::from)
                        .toList();

        // 아이 목록 응답 변환
        List<GuardianHomeResponse.ChildInfo> childInfos =
                children.stream()
                        .map(GuardianHomeResponse.ChildInfo::from)
                        .toList();

        return new GuardianHomeResponse(
                childInfos,
                selectedChildId,
                selectedChild.getNickname(),
                weekStart,
                weekEnd,
                questionCount,
                unrecognizedCount,
                pendingRequestCount,
                previews
        );
    }

    @Transactional
    public UpdateSelectedChildResponse updateSelectedChild(
            Long userId,
            Long childId
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new CustomException(GuardianErrorCode.ACCESS_FORBIDDEN)
                );

        Child child = childRepository.findById(childId)
                .orElseThrow(() ->
                        new CustomException(ChildErrorCode.CHILD_NOT_FOUND)
                );

        if (!child.getUser().getId().equals(userId)) {
            throw new CustomException(GuardianErrorCode.ACCESS_FORBIDDEN);
        }

        user.updateLastSelectedChildId(childId);
        return new UpdateSelectedChildResponse(childId);
    }
}

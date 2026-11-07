package com.moyamoya.domain.word.repository;

import com.moyamoya.domain.word.entity.WordRequest;
import com.moyamoya.domain.word.entity.WordRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface WordRequestRepository
        extends JpaRepository<WordRequest, Long> {

    List<WordRequest> findByChildIdOrderByCreatedAtDesc(Long childId);

    // 이번 주 전체 요청 수 (해결 여부 무관)
    long countByChildIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long childId,
            LocalDateTime start,
            LocalDateTime end
    );

    // 이번 주 미해결 요청 수
    long countByChildIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long childId,
            WordRequestStatus status,
            LocalDateTime start,
            LocalDateTime end
    );

    // 전체 기간 미해결 요청 수
    long countByChildIdAndStatus(
            Long childId,
            WordRequestStatus status
    );

    // 전체 기간 미해결 요청 최신 3개
    List<WordRequest> findTop3ByChildIdAndStatusOrderByCreatedAtDesc(
            Long childId,
            WordRequestStatus status
    );
}

package com.moyamoya.domain.word.repository;

import com.moyamoya.domain.word.entity.WordRequest;
import com.moyamoya.domain.word.entity.WordRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface WordRequestRepository
        extends JpaRepository<WordRequest, Long> {

    List<WordRequest> findByChildIdOrderByCreatedAtDesc(Long childId);

    long countByChildIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long childId,
            LocalDateTime start,
            LocalDateTime end
    );

    long countByChildIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long childId,
            WordRequestStatus status,
            LocalDateTime start,
            LocalDateTime end
    );

    long countByChildIdAndStatus(
            Long childId,
            WordRequestStatus status
    );

    List<WordRequest> findTop3ByChildIdAndStatusOrderByCreatedAtDesc(
            Long childId,
            WordRequestStatus status
    );

    // PENDING과 RESOLVED 모두 가져옴
    List<WordRequest>
    findByChildIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
            Long childId,
            LocalDateTime start,
            LocalDateTime end
    );
}
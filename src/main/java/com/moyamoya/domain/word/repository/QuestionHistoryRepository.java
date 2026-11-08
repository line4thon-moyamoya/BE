package com.moyamoya.domain.word.repository;

import com.moyamoya.domain.word.entity.QuestionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface QuestionHistoryRepository
        extends JpaRepository<QuestionHistory, Long> {

    long countByChildIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long childId,
            LocalDateTime start,
            LocalDateTime end
    );
}

package com.moyamoya.domain.word.repository;

import com.moyamoya.domain.word.entity.QuestionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.time.LocalDateTime;
import java.util.List;

public interface QuestionHistoryRepository
        extends JpaRepository<QuestionHistory, Long> {

    long countByChildIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long childId,
            LocalDateTime start,
            LocalDateTime end
    );
    
    @EntityGraph(attributePaths = "word")
    List<QuestionHistory>
    findByChildIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
            Long childId,
            LocalDateTime start,
            LocalDateTime end
    );
}
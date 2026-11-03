package com.moyamoya.domain.word.repository;

import com.moyamoya.domain.word.entity.QuestionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionHistoryRepository extends JpaRepository<QuestionHistory, Long> {
}

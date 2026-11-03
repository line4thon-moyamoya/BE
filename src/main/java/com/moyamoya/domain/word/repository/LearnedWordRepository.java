package com.moyamoya.domain.word.repository;

import com.moyamoya.domain.word.entity.LearnedWord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearnedWordRepository extends JpaRepository<LearnedWord, Long> {
}

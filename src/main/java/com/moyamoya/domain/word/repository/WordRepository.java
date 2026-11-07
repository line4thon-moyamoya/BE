package com.moyamoya.domain.word.repository;

import com.moyamoya.domain.word.entity.Word;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WordRepository extends JpaRepository<Word, Long> {

    Optional<Word> findByWord(String word);

    boolean existsByWord(String word);
}

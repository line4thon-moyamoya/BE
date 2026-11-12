package com.moyamoya.domain.word.repository;

import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.entity.WordSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WordRepository extends JpaRepository<Word, Long> {

    Optional<Word> findByWord(String word);

    boolean existsByWord(String word);

    List<Word> findAllBySourceType(WordSourceType sourceType);

    @Modifying
    @Query("""
    UPDATE Word w
    SET w.wordAudioUrl = :audioUrl
    WHERE w.id = :wordId
      AND (w.wordAudioUrl IS NULL OR w.wordAudioUrl = '')
    """)
    int updateWordAudioUrlIfAbsent(
            @Param("wordId") Long wordId,
            @Param("audioUrl") String audioUrl
    );
}

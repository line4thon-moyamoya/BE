package com.moyamoya.domain.song.repository;

import com.moyamoya.domain.word.entity.Word;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface SongWordAudioRepository extends JpaRepository<Word, Long> {
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    Optional<Word> findByWord(String word);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            update Word w set w.songAudioUrl = :key
            where w.id = :id and w.word = :word
              and (w.songAudioUrl = :previous or (w.songAudioUrl is null and :previous is null))
            """)
    int replaceAudioKey(@Param("id") Long id, @Param("word") String word,
            @Param("previous") String previous, @Param("key") String key);
}

package com.moyamoya.domain.song.repository;

import com.moyamoya.domain.song.entity.SongWord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SongWordRepository extends JpaRepository<SongWord, Long> {
    @Query("""
            select sw from SongWord sw
            join fetch sw.song s
            join fetch sw.learnedWord lw
            join fetch lw.word
            where s.child.id = :childId
            order by s.id desc, sw.wordOrder asc
            """)
    List<SongWord> findAllWithWordByChildId(@Param("childId") Long childId);

    @EntityGraph(attributePaths = "learnedWord.word")
    List<SongWord> findAllBySong_IdOrderByWordOrderAsc(Long songId);
}

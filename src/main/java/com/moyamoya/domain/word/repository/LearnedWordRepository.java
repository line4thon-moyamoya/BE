package com.moyamoya.domain.word.repository;

import com.moyamoya.domain.word.entity.LearnedWord;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LearnedWordRepository extends JpaRepository<LearnedWord, Long> {
    @Query("""
            select count(lw) from LearnedWord lw
            where lw.child.id = :childId
              and not exists (
                  select sw.id from SongWord sw where sw.learnedWord = lw
              )
            """)
    long countUnusedForSongByChildId(@Param("childId") Long childId);

    @Query("""
            select lw from LearnedWord lw
            where lw.child.id = :childId
              and not exists (
                  select sw.id from SongWord sw where sw.learnedWord = lw
              )
            order by lw.learnedAt asc, lw.id asc
            """)
    List<LearnedWord> findUnusedForSongByChildId(@Param("childId") Long childId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LearnedWord> findByChild_IdAndWord_Id(Long childId, Long wordId);

    @EntityGraph(attributePaths = "word")
    Optional<LearnedWord> findByIdAndChild_Id(Long learnedWordId, Long childId);

    long countByChild_Id(Long childId);

    @Query("""
            select lw from LearnedWord lw
            join fetch lw.word
            where lw.child.id = :childId
            order by lw.learnedAt desc, lw.id desc
            """)
    List<LearnedWord> findAllWithWordByChildId(@Param("childId") Long childId);
}

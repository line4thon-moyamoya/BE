package com.moyamoya.domain.word.repository;

import com.moyamoya.domain.word.entity.LearnedWord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LearnedWordRepository extends JpaRepository<LearnedWord, Long> {
    Optional<LearnedWord> findByIdAndChild_Id(Long learnedWordId, Long childId);

    @Query("""
            select lw from LearnedWord lw
            join fetch lw.word
            where lw.child.id = :childId
            order by lw.learnedAt desc, lw.id desc
            """)
    List<LearnedWord> findAllWithWordByChildId(@Param("childId") Long childId);
}

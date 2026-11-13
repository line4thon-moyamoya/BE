package com.moyamoya.domain.song.repository;

import com.moyamoya.domain.song.entity.Song;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SongRepository extends JpaRepository<Song, Long> {
    long countByChild_Id(Long childId);

    List<Song> findAllByChild_IdOrderByCreatedAtDescIdDesc(Long childId);

    Optional<Song> findByIdAndChild_Id(Long songId, Long childId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Song s where s.id = :songId")
    Optional<Song> findByIdForUpdate(@Param("songId") Long songId);
}

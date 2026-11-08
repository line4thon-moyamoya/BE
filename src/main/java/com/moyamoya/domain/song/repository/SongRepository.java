package com.moyamoya.domain.song.repository;

import com.moyamoya.domain.song.entity.Song;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SongRepository extends JpaRepository<Song, Long> {
    long countByChild_Id(Long childId);
}

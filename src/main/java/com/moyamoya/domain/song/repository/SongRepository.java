package com.moyamoya.domain.song.repository;

import com.moyamoya.domain.song.entity.Song;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SongRepository extends JpaRepository<Song, Long> {
    long countByChild_Id(Long childId);

    List<Song> findAllByChild_IdOrderByCreatedAtDescIdDesc(Long childId);
}

package com.moyamoya.domain.song.repository;

import com.moyamoya.domain.song.entity.SongWord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SongWordRepository extends JpaRepository<SongWord, Long> {
}

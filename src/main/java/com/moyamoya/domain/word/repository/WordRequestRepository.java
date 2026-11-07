package com.moyamoya.domain.word.repository;

import com.moyamoya.domain.word.entity.WordRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WordRequestRepository
        extends JpaRepository<WordRequest, Long> {

    List<WordRequest> findByChildIdOrderByCreatedAtDesc(Long childId);
}

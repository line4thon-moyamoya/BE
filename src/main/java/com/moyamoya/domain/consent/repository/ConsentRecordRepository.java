package com.moyamoya.domain.consent.repository;

import com.moyamoya.domain.consent.entity.ConsentRecord;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {

    @EntityGraph(attributePaths = "consent")
    List<ConsentRecord> findByUser_Id(Long userId);

    Optional<ConsentRecord> findByUser_IdAndConsent_Id(Long userId, Long consentId);
}

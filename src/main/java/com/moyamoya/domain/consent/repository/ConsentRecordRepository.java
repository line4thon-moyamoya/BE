package com.moyamoya.domain.consent.repository;

import com.moyamoya.domain.consent.entity.ConsentRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {

    Optional<ConsentRecord> findByUser_IdAndConsent_Id(Long userId, Long consentId);
}

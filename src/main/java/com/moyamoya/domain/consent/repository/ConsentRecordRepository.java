package com.moyamoya.domain.consent.repository;

import com.moyamoya.domain.consent.entity.ConsentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {
}

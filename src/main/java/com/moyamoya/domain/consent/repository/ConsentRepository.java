package com.moyamoya.domain.consent.repository;

import com.moyamoya.domain.consent.entity.Consent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, Long> {
}

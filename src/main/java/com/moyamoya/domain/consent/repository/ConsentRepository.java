package com.moyamoya.domain.consent.repository;

import com.moyamoya.domain.consent.entity.Consent;
import com.moyamoya.domain.consent.entity.ConsentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

    List<Consent> findAllByOrderByIdAsc();

    Optional<Consent> findByConsentType(ConsentType consentType);
}

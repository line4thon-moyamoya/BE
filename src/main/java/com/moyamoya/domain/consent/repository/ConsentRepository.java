package com.moyamoya.domain.consent.repository;

import com.moyamoya.domain.consent.entity.Consent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

    List<Consent> findAllByOrderByIdAsc();
}

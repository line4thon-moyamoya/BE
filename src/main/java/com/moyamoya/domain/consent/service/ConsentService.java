package com.moyamoya.domain.consent.service;

import com.moyamoya.domain.consent.dto.response.ConsentResponse;
import com.moyamoya.domain.consent.repository.ConsentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsentService {

    private final ConsentRepository consentRepository;

    @Transactional(readOnly = true)
    public List<ConsentResponse> getConsents() {
        return consentRepository.findAllByOrderByIdAsc().stream()
                .map(ConsentResponse::from)
                .toList();
    }
}

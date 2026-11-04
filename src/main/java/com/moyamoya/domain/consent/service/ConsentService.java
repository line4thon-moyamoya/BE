package com.moyamoya.domain.consent.service;

import com.moyamoya.domain.consent.dto.request.ConsentStatusUpdateRequest;
import com.moyamoya.domain.consent.dto.response.ConsentResponse;
import com.moyamoya.domain.consent.dto.response.ConsentStatusResponse;
import com.moyamoya.domain.consent.entity.Consent;
import com.moyamoya.domain.consent.entity.ConsentRecord;
import com.moyamoya.domain.consent.entity.ConsentType;
import com.moyamoya.domain.consent.exception.ConsentErrorCode;
import com.moyamoya.domain.consent.repository.ConsentRecordRepository;
import com.moyamoya.domain.consent.repository.ConsentRepository;
import com.moyamoya.global.exception.CustomException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsentService {

    private final ConsentRepository consentRepository;
    private final ConsentRecordRepository consentRecordRepository;

    @Transactional(readOnly = true)
    public List<ConsentResponse> getConsents() {
        return consentRepository.findAllByOrderByIdAsc().stream()
                .map(ConsentResponse::from)
                .toList();
    }

    @Transactional
    public ConsentStatusResponse updateVoiceProcessingConsent(
            Long userId,
            ConsentStatusUpdateRequest request
    ) {
        if (request == null || request.isAgreed() == null) {
            throw new CustomException(ConsentErrorCode.INVALID_CONSENT_REQUEST);
        }

        Consent consent = consentRepository.findByConsentType(ConsentType.VOICE_PROCESSING)
                .orElseThrow(() -> new CustomException(ConsentErrorCode.CONSENT_NOT_FOUND));
        ConsentRecord consentRecord = consentRecordRepository
                .findByUser_IdAndConsent_Id(userId, consent.getId())
                .orElseThrow(() -> new CustomException(
                        ConsentErrorCode.CONSENT_RECORD_NOT_FOUND
                ));

        consentRecord.updateAgreement(request.isAgreed());
        return new ConsentStatusResponse(ConsentType.VOICE_PROCESSING, request.isAgreed());
    }
}

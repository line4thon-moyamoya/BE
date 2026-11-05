package com.moyamoya.domain.consent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    @Mock
    private ConsentRepository consentRepository;

    @Mock
    private ConsentRecordRepository consentRecordRepository;

    private ConsentService consentService;

    @BeforeEach
    void setUp() {
        consentService = new ConsentService(consentRepository, consentRecordRepository);
    }

    @Test
    void getConsentsReturnsItemsInRepositoryOrder() {
        Consent terms = consent(1L, ConsentType.TERMS_OF_SERVICE, "서비스 이용약관 내용");
        Consent privacy = consent(2L, ConsentType.PRIVACY_POLICY, "개인정보 처리방침 내용");
        when(consentRepository.findAllByOrderByIdAsc()).thenReturn(List.of(terms, privacy));

        List<ConsentResponse> response = consentService.getConsents();

        assertThat(response).containsExactly(
                new ConsentResponse(1L, ConsentType.TERMS_OF_SERVICE, "서비스 이용약관 내용"),
                new ConsentResponse(2L, ConsentType.PRIVACY_POLICY, "개인정보 처리방침 내용")
        );
        verify(consentRepository).findAllByOrderByIdAsc();
    }

    @Test
    void getConsentsReturnsEmptyListWhenNoItemsExist() {
        when(consentRepository.findAllByOrderByIdAsc()).thenReturn(List.of());

        List<ConsentResponse> response = consentService.getConsents();

        assertThat(response).isEmpty();
    }

    @Test
    void updateVoiceProcessingConsentChangesStoredStatus() {
        Consent consent = mock(Consent.class);
        ConsentRecord consentRecord = mock(ConsentRecord.class);
        when(consent.getId()).thenReturn(4L);
        when(consentRepository.findByConsentType(ConsentType.VOICE_PROCESSING))
                .thenReturn(Optional.of(consent));
        when(consentRecordRepository.findByUser_IdAndConsent_Id(1L, 4L))
                .thenReturn(Optional.of(consentRecord));

        ConsentStatusResponse response = consentService.updateVoiceProcessingConsent(
                1L,
                new ConsentStatusUpdateRequest(false)
        );

        assertThat(response).isEqualTo(
                new ConsentStatusResponse(ConsentType.VOICE_PROCESSING, false)
        );
        verify(consentRecord).updateAgreement(false);
    }

    @Test
    void updateVoiceProcessingConsentRejectsMissingAgreementStatus() {
        ConsentStatusUpdateRequest request = new ConsentStatusUpdateRequest(null);

        assertThatThrownBy(() -> consentService.updateVoiceProcessingConsent(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ConsentErrorCode.INVALID_CONSENT_REQUEST);
    }

    @Test
    void updateVoiceProcessingConsentRejectsUnknownConsent() {
        when(consentRepository.findByConsentType(ConsentType.VOICE_PROCESSING))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> consentService.updateVoiceProcessingConsent(
                1L,
                new ConsentStatusUpdateRequest(false)
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ConsentErrorCode.CONSENT_NOT_FOUND);
    }

    @Test
    void updateVoiceProcessingConsentRejectsMissingUserConsentRecord() {
        Consent consent = mock(Consent.class);
        when(consent.getId()).thenReturn(4L);
        when(consentRepository.findByConsentType(ConsentType.VOICE_PROCESSING))
                .thenReturn(Optional.of(consent));
        when(consentRecordRepository.findByUser_IdAndConsent_Id(1L, 4L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> consentService.updateVoiceProcessingConsent(
                1L,
                new ConsentStatusUpdateRequest(false)
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ConsentErrorCode.CONSENT_RECORD_NOT_FOUND);
    }

    private Consent consent(Long id, ConsentType type, String content) {
        Consent consent = mock(Consent.class);
        when(consent.getId()).thenReturn(id);
        when(consent.getConsentType()).thenReturn(type);
        when(consent.getContent()).thenReturn(content);
        return consent;
    }
}

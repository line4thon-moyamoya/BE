package com.moyamoya.domain.consent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moyamoya.domain.consent.dto.response.ConsentResponse;
import com.moyamoya.domain.consent.entity.Consent;
import com.moyamoya.domain.consent.entity.ConsentType;
import com.moyamoya.domain.consent.repository.ConsentRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    @Mock
    private ConsentRepository consentRepository;

    private ConsentService consentService;

    @BeforeEach
    void setUp() {
        consentService = new ConsentService(consentRepository);
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

    private Consent consent(Long id, ConsentType type, String content) {
        Consent consent = mock(Consent.class);
        when(consent.getId()).thenReturn(id);
        when(consent.getConsentType()).thenReturn(type);
        when(consent.getContent()).thenReturn(content);
        return consent;
    }
}

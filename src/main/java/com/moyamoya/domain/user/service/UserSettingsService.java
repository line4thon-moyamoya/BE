package com.moyamoya.domain.user.service;

import com.moyamoya.domain.child.dto.response.ChildResponse;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.consent.entity.ConsentType;
import com.moyamoya.domain.consent.repository.ConsentRecordRepository;
import com.moyamoya.domain.user.dto.response.UserSettingsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserSettingsService {
    private final ChildRepository childRepository;
    private final ConsentRecordRepository consentRecordRepository;

    @Transactional(readOnly = true)
    public UserSettingsResponse getSettings(Long userId) {
        var children = childRepository.findByUserIdOrderByIdAsc(userId).stream()
                .map(ChildResponse::from).toList();
        var records = consentRecordRepository.findByUser_Id(userId);
        boolean processing = records.stream().anyMatch(record -> record.isAgreed()
                && record.getConsent().getConsentType() == ConsentType.VOICE_PROCESSING);
        boolean improvement = records.stream().anyMatch(record -> record.isAgreed()
                && record.getConsent().getConsentType() == ConsentType.VOICE_IMPROVEMENT);
        return new UserSettingsResponse(children, processing, improvement);
    }
}

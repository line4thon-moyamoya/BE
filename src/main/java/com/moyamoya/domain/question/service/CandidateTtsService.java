package com.moyamoya.domain.question.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Base64;

@Service
@RequiredArgsConstructor
public class CandidateTtsService {

    private final MoyaTtsService moyaTtsService;

    public String generateBase64IfNeeded(
            String text,
            String existingUrl
    ) {
        if (StringUtils.hasText(existingUrl)) {
            return null;
        }

        if (!StringUtils.hasText(text)) {
            return null;
        }

        byte[] wav = moyaTtsService.synthesize(text);
        return Base64.getEncoder().encodeToString(wav);
    }
}

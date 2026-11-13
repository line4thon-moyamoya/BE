package com.moyamoya.domain.question.service;

import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.repository.WordRepository;
import com.moyamoya.global.s3.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Base64;

@Service
@RequiredArgsConstructor
public class CandidateTtsService {

    private final MoyaTtsService moyaTtsService;
    private final S3Service s3Service;
    private final WordRepository wordRepository;

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

        byte[] audio = moyaTtsService.synthesize(text);
        return Base64.getEncoder().encodeToString(audio);
    }

    public String resolveGeneratedAudioContentType() {
        return moyaTtsService.getContentType();
    }

    public String ensureStoredAudio(Word word) {
        if (StringUtils.hasText(word.getWordAudioUrl())) {
            return word.getWordAudioUrl();
        }

        // TTS 생성 및 S3 업로드
        byte[] audio = moyaTtsService.synthesize(word.getWord());

        String key = "words/audio/" + word.getId() + "/word.wav";
        String uploadKey = s3Service.uploadWav(key, audio);

        // Word 전체가 아닌 음성 URL만 조건부 업데이트
        int updated = wordRepository.updateWordAudioUrlIfAbsent(
                word.getId(),
                uploadKey
        );

        if (updated == 1) {
            return uploadKey;
        }

        // 다른 요청이 먼저 저장했다면 DB의 최신 값 사용
        return wordRepository.findById(word.getId())
                .map(Word::getWordAudioUrl)
                .orElse(null);
    }

    public String resolveAudioUrl(String s3Key) {
        return resolvePresignedUrl(s3Key);
    }

    public String resolvePresignedUrl(String s3Key) {
        if (!StringUtils.hasText(s3Key)) {
            return null;
        }

        return s3Service.getPresignedUrl(s3Key);
    }
}
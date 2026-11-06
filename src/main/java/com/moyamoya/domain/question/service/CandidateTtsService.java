package com.moyamoya.domain.question.service;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.repository.WordRepository;
import com.moyamoya.global.s3.S3AudioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Base64;

@Service
@RequiredArgsConstructor
public class CandidateTtsService {

    private final MoyaTtsService moyaTtsService;
    private final S3AudioService s3AudioService;
    private final WordRepository wordRepository;

    // 신규 외부 단어: S3에 저장하지 않고 Base64만 생성
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

    // DB에 존재하는 단어의 음성을 S3에 저장
    public void ensureStoredAudio(Word word, AgeGroup ageGroup) {

        // 1. 단어 발음 음성이 없으면 생성 후 업로드
        if (!StringUtils.hasText(word.getWordAudioUrl())) {

            byte[] wav = moyaTtsService.synthesize(word.getWord());

            String key = "words/audio/" + word.getId() + "/word.wav";

            String uploadKey = s3AudioService.uploadWav(key, wav);
            word.updateWordAudioUrl(uploadKey);
        }

        // 2. 현재 아이 연령대에 해당하는 설명 음성만 생성
        boolean age3To4 = ageGroup == AgeGroup.AGE_3_4;

        String description = age3To4
                ? word.getDescAge3To4()
                : word.getDescAge5();

        String existingKey = age3To4
                ? word.getDescriptionAudioAge3To4Url()
                : word.getDescriptionAudioAge5Url();

        // 연령별 설명이 없다면 기본 정의 사용
        if (!StringUtils.hasText(description)) {
            description = word.getDefinition();
        }

        if (!StringUtils.hasText(existingKey)
                && StringUtils.hasText(description)) {

            byte[] wav = moyaTtsService.synthesize(description);

            String key = age3To4
                    ? "words/audio/" + word.getId() + "/desc-age-3-4.wav"
                    : "words/audio/" + word.getId() + "/desc-age-5.wav";

            String uploadKey = s3AudioService.uploadWav(key, wav);

            if (age3To4) {
                word.updateDescriptionAudioAge3To4Url(uploadKey);
            } else {
                word.updateDescriptionAudioAge5Url(uploadKey);
            }
        }

        // 3. 변경된 S3 객체 키를 MySQL에 저장
        wordRepository.save(word);
    }

    // DB에 저장된 S3 객체 키를 재생 URL로 변환
    public String resolveAudioUrl(String s3Key) {
        if (!StringUtils.hasText(s3Key)) {
            return null;
        }

        return s3AudioService.getPresignedUrl(s3Key);
    }
}
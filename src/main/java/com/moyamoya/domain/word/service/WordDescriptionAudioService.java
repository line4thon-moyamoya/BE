package com.moyamoya.domain.word.service;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.question.service.CandidateTtsService;
import com.moyamoya.domain.word.dto.response.WordDescriptionResponse;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.exception.WordErrorCode;
import com.moyamoya.global.exception.CustomException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class WordDescriptionAudioService {
    private final CandidateTtsService candidateTtsService;
    private final EntityManager entityManager;

    /**
     * 조회 트랜잭션이 끝난 뒤 누락된 음성을 준비한다.
     * 음성 생성 시에는 단어를 잠금 조회하고 재확인하여 이 경로의 중복 생성을 방지한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WordDescriptionResponse resolve(Word word, AgeGroup ageGroup) {
        String description = description(word, ageGroup);
        String audioKey = audioKey(word, ageGroup);
        if (!StringUtils.hasText(audioKey) && StringUtils.hasText(description)) {
            word = entityManager.find(Word.class, word.getId(), LockModeType.PESSIMISTIC_WRITE);
            if (word == null) {
                throw new CustomException(WordErrorCode.WORD404);
            }
            if (!StringUtils.hasText(audioKey(word, ageGroup))) {
                candidateTtsService.ensureStoredAudio(word, ageGroup);
            }
            description = description(word, ageGroup);
            audioKey = audioKey(word, ageGroup);
            if (!StringUtils.hasText(audioKey)) {
                throw new IllegalStateException("단어 설명 음성이 준비되지 않았습니다.");
            }
        }
        String audioUrl = StringUtils.hasText(audioKey)
                ? candidateTtsService.resolveAudioUrl(audioKey) : null;
        return new WordDescriptionResponse(word.getId(), word.getWord(), description, audioUrl);
    }

    private String description(Word word, AgeGroup ageGroup) {
        String description = switch (ageGroup) {
            case AGE_3_4 -> word.getDescAge3To4();
            case AGE_5 -> word.getDescAge5();
        };
        return StringUtils.hasText(description) ? description : word.getDefinition();
    }

    private String audioKey(Word word, AgeGroup ageGroup) {
        return switch (ageGroup) {
            case AGE_3_4 -> word.getDescriptionAudioAge3To4Url();
            case AGE_5 -> word.getDescriptionAudioAge5Url();
        };
    }
}

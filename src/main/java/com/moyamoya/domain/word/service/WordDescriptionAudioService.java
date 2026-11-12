package com.moyamoya.domain.word.service;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.question.service.CandidateTtsService;
import com.moyamoya.domain.word.dto.response.WordDescriptionResponse;
import com.moyamoya.domain.word.entity.Word;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class WordDescriptionAudioService {
    private final CandidateTtsService candidateTtsService;

    /**
     * 조회 트랜잭션이 끝난 뒤 누락된 단어 발음 음성을 준비한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WordDescriptionResponse resolve(Word word, AgeGroup ageGroup) {
        String description = description(word, ageGroup);

        String audioKey = word.getWordAudioUrl();

        if (!StringUtils.hasText(audioKey)) {
            audioKey = candidateTtsService.ensureStoredAudio(word);
        }

        String wordAudioUrl =
                candidateTtsService.resolveAudioUrl(audioKey);

        return new WordDescriptionResponse(
                word.getId(),
                word.getWord(),
                description,
                wordAudioUrl
        );
    }

    private String description(Word word, AgeGroup ageGroup) {
        String description = switch (ageGroup) {
            case AGE_3_4 -> word.getDescAge3To4();
            case AGE_5 -> word.getDescAge5();
        };
        return StringUtils.hasText(description) ? description : word.getDefinition();
    }
}

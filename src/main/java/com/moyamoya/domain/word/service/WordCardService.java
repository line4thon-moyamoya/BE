package com.moyamoya.domain.word.service;

import com.moyamoya.domain.word.dto.response.WordCardDetailResponse;
import com.moyamoya.domain.word.dto.response.WordDescriptionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WordCardService {
    private final WordLookupService wordLookupService;
    private final WordDescriptionAudioService wordDescriptionAudioService;

    public WordCardDetailResponse getWordCardDetail(Long userId, Long childId, Long learnedWordId) {
        var lookup = wordLookupService.findCard(userId, childId, learnedWordId);
        WordDescriptionResponse description = wordDescriptionAudioService.resolve(lookup.word(), lookup.ageGroup());
        return new WordCardDetailResponse(lookup.learnedWordId(), description.wordId(), description.word(),
                description.description(), lookup.imageUrl(), description.descriptionAudioUrl());
    }
}

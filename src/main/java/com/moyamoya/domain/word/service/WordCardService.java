package com.moyamoya.domain.word.service;

import com.moyamoya.domain.word.dto.response.WordCardDetailResponse;
import com.moyamoya.domain.word.dto.response.WordDescriptionResponse;
import com.moyamoya.global.s3.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WordCardService {
    private final WordLookupService wordLookupService;
    private final WordDescriptionAudioService wordDescriptionAudioService;
    private final S3Service s3Service;

    public WordCardDetailResponse getWordCardDetail(Long userId, Long childId, Long learnedWordId) {
        var lookup = wordLookupService.findCard(userId, childId, learnedWordId);
        WordDescriptionResponse description = wordDescriptionAudioService.resolve(lookup.word(), lookup.ageGroup());
        String imageUrl = lookup.imageUrl() == null
                ? null
                : s3Service.getPresignedUrl(lookup.imageUrl());
        return new WordCardDetailResponse(lookup.learnedWordId(), description.wordId(), description.word(),
                description.description(), imageUrl, description.wordAudioUrl());
    }
}

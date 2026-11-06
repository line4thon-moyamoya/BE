package com.moyamoya.domain.word.service;

import com.moyamoya.domain.word.dto.response.WordDescriptionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WordService {
    private final WordLookupService wordLookupService;
    private final WordDescriptionAudioService wordDescriptionAudioService;

    public WordDescriptionResponse getWordDescription(Long userId, Long wordId, Long childId) {
        var lookup = wordLookupService.findWord(userId, wordId, childId);
        return wordDescriptionAudioService.resolve(lookup.word(), lookup.ageGroup());
    }
}

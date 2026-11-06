package com.moyamoya.domain.word.service;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.word.dto.response.WordDescriptionResponse;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.exception.WordErrorCode;
import com.moyamoya.domain.word.repository.WordRepository;
import com.moyamoya.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WordService {
    private final ChildRepository childRepository;
    private final WordRepository wordRepository;
    private final WordDescriptionAudioService wordDescriptionAudioService;

    @Transactional(readOnly = true)
    public WordDescriptionResponse getWordDescription(Long userId, Long wordId, Long childId) {
        if (wordId == null || wordId <= 0) {
            throw new CustomException(WordErrorCode.INVALID_WORD_ID);
        }
        if (childId == null || childId <= 0) {
            throw new CustomException(WordErrorCode.INVALID_CHILD_ID);
        }
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new CustomException(WordErrorCode.CHILD_NOT_FOUND));
        if (!child.getUser().getId().equals(userId)) {
            throw new CustomException(WordErrorCode.CHILD_ACCESS_DENIED);
        }
        Word word = wordRepository.findById(wordId)
                .orElseThrow(() -> new CustomException(WordErrorCode.WORD404));
        return wordDescriptionAudioService.resolve(word, child.getAgeGroup());
    }
}

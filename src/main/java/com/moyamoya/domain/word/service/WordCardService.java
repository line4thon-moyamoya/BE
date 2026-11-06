package com.moyamoya.domain.word.service;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.word.dto.response.WordCardDetailResponse;
import com.moyamoya.domain.word.entity.LearnedWord;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.exception.WordErrorCode;
import com.moyamoya.domain.word.repository.LearnedWordRepository;
import com.moyamoya.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WordCardService {
    private final ChildRepository childRepository;
    private final LearnedWordRepository learnedWordRepository;

    @Transactional(readOnly = true)
    public WordCardDetailResponse getWordCardDetail(Long userId, Long childId, Long learnedWordId) {
        if (childId == null || childId <= 0) {
            throw new CustomException(WordErrorCode.INVALID_CHILD_ID);
        }
        if (learnedWordId == null || learnedWordId <= 0) {
            throw new CustomException(WordErrorCode.INVALID_LEARNED_WORD_ID);
        }
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new CustomException(WordErrorCode.CHILD_NOT_FOUND));
        if (!child.getUser().getId().equals(userId)) {
            throw new CustomException(WordErrorCode.CHILD_ACCESS_DENIED);
        }
        LearnedWord card = learnedWordRepository.findByIdAndChild_Id(learnedWordId, childId)
                .orElseThrow(() -> new CustomException(WordErrorCode.LEARNED_WORD_NOT_FOUND));
        Word word = card.getWord();
        if (word == null) {
            throw new CustomException(WordErrorCode.WORD404);
        }
        return switch (child.getAgeGroup()) {
            case AGE_3_4 -> new WordCardDetailResponse(card.getId(), word.getId(), word.getWord(),
                    word.getDescAge3To4(), word.getImageUrl(), word.getDescriptionAudioAge3To4Url());
            case AGE_5 -> new WordCardDetailResponse(card.getId(), word.getId(), word.getWord(),
                    word.getDescAge5(), word.getImageUrl(), word.getDescriptionAudioAge5Url());
        };
    }
}

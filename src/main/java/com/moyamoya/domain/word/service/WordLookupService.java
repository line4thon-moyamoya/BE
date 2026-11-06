package com.moyamoya.domain.word.service;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.word.entity.LearnedWord;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.exception.WordErrorCode;
import com.moyamoya.domain.word.repository.LearnedWordRepository;
import com.moyamoya.domain.word.repository.WordRepository;
import com.moyamoya.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WordLookupService {
    private final ChildRepository childRepository;
    private final WordRepository wordRepository;
    private final LearnedWordRepository learnedWordRepository;

    @Transactional(readOnly = true)
    public LookupResult findWord(Long userId, Long wordId, Long childId) {
        if (wordId == null || wordId <= 0) {
            throw new CustomException(WordErrorCode.INVALID_WORD_ID);
        }
        Child child = ownedChild(userId, childId);
        Word word = wordRepository.findById(wordId)
                .orElseThrow(() -> new CustomException(WordErrorCode.WORD404));
        return new LookupResult(word, child.getAgeGroup(), null, word.getImageUrl());
    }

    @Transactional(readOnly = true)
    public LookupResult findCard(Long userId, Long childId, Long learnedWordId) {
        if (childId == null || childId <= 0) {
            throw new CustomException(WordErrorCode.INVALID_CHILD_ID);
        }
        if (learnedWordId == null || learnedWordId <= 0) {
            throw new CustomException(WordErrorCode.INVALID_LEARNED_WORD_ID);
        }
        Child child = ownedChild(userId, childId);
        LearnedWord card = learnedWordRepository.findByIdAndChild_Id(learnedWordId, childId)
                .orElseThrow(() -> new CustomException(WordErrorCode.LEARNED_WORD_NOT_FOUND));
        Word word = card.getWord();
        if (word == null) {
            throw new CustomException(WordErrorCode.WORD404);
        }
        return new LookupResult(word, child.getAgeGroup(), card.getId(), word.getImageUrl());
    }

    private Child ownedChild(Long userId, Long childId) {
        if (childId == null || childId <= 0) {
            throw new CustomException(WordErrorCode.INVALID_CHILD_ID);
        }
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new CustomException(WordErrorCode.CHILD_NOT_FOUND));
        if (!child.getUser().getId().equals(userId)) {
            throw new CustomException(WordErrorCode.CHILD_ACCESS_DENIED);
        }
        return child;
    }

    public record LookupResult(Word word, AgeGroup ageGroup, Long learnedWordId, String imageUrl) {}
}

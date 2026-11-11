package com.moyamoya.domain.wordbook.service;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.word.repository.LearnedWordRepository;
import com.moyamoya.domain.word.entity.LearnedWord;
import com.moyamoya.domain.wordbook.dto.response.LearnedWordResponse;
import com.moyamoya.domain.wordbook.dto.response.WordbookResponse;
import com.moyamoya.domain.wordbook.exception.WordbookErrorCode;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.s3.S3Service;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WordbookService {
    private final ChildRepository childRepository;
    private final LearnedWordRepository learnedWordRepository;
    private final S3Service s3Service;

    @Transactional(readOnly = true)
    public WordbookResponse getWordbook(Long userId, Long childId) {
        validateChildOwnership(userId, childId);
        List<LearnedWordResponse> cards = learnedWordRepository.findAllWithWordByChildId(childId)
                .stream()
                .map(learnedWord -> {
                    String imageKey = learnedWord.getWord().getImageUrl();
                    String imagePresignedUrl = imageKey == null
                            ? null
                            : s3Service.getPresignedUrl(imageKey);
                    return LearnedWordResponse.from(learnedWord, imagePresignedUrl);
                })
                .toList();
        return new WordbookResponse(cards.size(), cards);
    }

    @Transactional
    public void markAsViewed(Long userId, Long childId, Long learnedWordId) {
        validateChildOwnership(userId, childId);
        LearnedWord card = learnedWordRepository.findByIdAndChild_Id(learnedWordId, childId)
                .orElseThrow(() -> new CustomException(WordbookErrorCode.LEARNED_WORD_NOT_FOUND));
        card.markAsViewed();
    }

    private void validateChildOwnership(Long userId, Long childId) {
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new CustomException(WordbookErrorCode.CHILD_NOT_FOUND));
        if (!child.getUser().getId().equals(userId)) {
            throw new CustomException(WordbookErrorCode.CHILD_ACCESS_DENIED);
        }
    }
}

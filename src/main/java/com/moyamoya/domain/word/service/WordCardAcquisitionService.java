package com.moyamoya.domain.word.service;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.song.model.SongRegistrationResult;
import com.moyamoya.domain.song.model.SongWordAudioPreparationRequested;
import com.moyamoya.domain.song.service.SongRegistrationService;
import com.moyamoya.domain.word.entity.LearnedWord;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.exception.WordErrorCode;
import com.moyamoya.domain.word.model.CardAcquisitionResult;
import com.moyamoya.domain.word.repository.LearnedWordRepository;
import com.moyamoya.domain.word.repository.WordRepository;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.security.exception.SecurityErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WordCardAcquisitionService {
    private final ChildRepository childRepository;
    private final WordRepository wordRepository;
    private final LearnedWordRepository learnedWordRepository;
    private final SongRegistrationService songRegistrationService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CardAcquisitionResult acquireCard(Long userId, Long childId, Long wordId, boolean isCorrect) {
        if (userId == null || userId <= 0) {
            throw new CustomException(SecurityErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (childId == null || childId <= 0) {
            throw new CustomException(WordErrorCode.INVALID_CHILD_ID);
        }
        if (wordId == null || wordId <= 0) {
            throw new CustomException(WordErrorCode.INVALID_WORD_ID);
        }
        Child child = childRepository.findByIdForUpdate(childId)
                .orElseThrow(() -> new CustomException(WordErrorCode.CHILD_NOT_FOUND));
        if (!child.getUser().getId().equals(userId)) {
            throw new CustomException(WordErrorCode.CHILD_ACCESS_DENIED);
        }
        Word word = wordRepository.findById(wordId)
                .orElseThrow(() -> new CustomException(WordErrorCode.WORD404));
        LearnedWord card = learnedWordRepository.findByChild_IdAndWord_Id(childId, wordId).orElse(null);
        boolean isNewAcquisition = card == null;
        if (isNewAcquisition) {
            card = learnedWordRepository.save(LearnedWord.create(child, word, isCorrect));
        } else if (isCorrect) {
            card.earnSticker();
        }
        SongRegistrationResult registration = isNewAcquisition
                ? songRegistrationService.register(child)
                : new SongRegistrationResult(learnedWordRepository.countUnusedForSongByChildId(childId), null);
        if (isNewAcquisition) {
            eventPublisher.publishEvent(new SongWordAudioPreparationRequested(word.getWord()));
        }
        return new CardAcquisitionResult(card.getId(), isNewAcquisition, card.isStickerEarned(),
                registration.couponCount(), registration.songId());
    }
}

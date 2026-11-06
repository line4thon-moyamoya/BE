package com.moyamoya.domain.word.service;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.question.exception.QuestionErrorCode;
import com.moyamoya.domain.question.service.SttService;
import com.moyamoya.domain.word.dto.response.RepetitionResponse;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.exception.RepetitionErrorCode;
import com.moyamoya.domain.word.exception.WordErrorCode;
import com.moyamoya.domain.word.repository.WordRepository;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.security.exception.SecurityErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class RepetitionService {
    private final ChildRepository childRepository;
    private final WordRepository wordRepository;
    private final SttService sttService;
    private final RepetitionEvaluationService evaluationService;
    private final WordCardAcquisitionService acquisitionService;

    public RepetitionResponse repeat(Long userId, Long childId, Long wordId, MultipartFile audio) {
        if (userId == null || userId <= 0) {
            throw new CustomException(SecurityErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (childId == null || childId <= 0) {
            throw new CustomException(WordErrorCode.INVALID_CHILD_ID);
        }
        if (wordId == null || wordId <= 0) {
            throw new CustomException(WordErrorCode.INVALID_WORD_ID);
        }
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new CustomException(WordErrorCode.CHILD_NOT_FOUND));
        if (!child.getUser().getId().equals(userId)) {
            throw new CustomException(WordErrorCode.CHILD_ACCESS_DENIED);
        }
        Word word = wordRepository.findById(wordId)
                .orElseThrow(() -> new CustomException(WordErrorCode.WORD404));

        String recognizedText = transcribe(audio);
        var evaluation = evaluationService.evaluate(recognizedText, word.getWord(), child.getAgeGroup());
        // 외부 호출이 끝난 뒤 별도 트랜잭션에서 카드 저장 및 소유권 재확인
        var acquisition = acquisitionService.acquireCard(userId, childId, wordId, evaluation.isCorrect());
        return new RepetitionResponse(wordId, acquisition.learnedWordId(),
                evaluation.isCorrect(), acquisition.isNewAcquisition());
    }

    private String transcribe(MultipartFile audio) {
        try {
            return sttService.transcribe(audio);
        } catch (CustomException exception) {
            if (exception.getErrorCode() == QuestionErrorCode.STT_NO_SPEECH) {
                return "";
            }
            if (exception.getErrorCode() == QuestionErrorCode.INVALID_AUDIO_FILE) {
                throw new CustomException(RepetitionErrorCode.INVALID_AUDIO);
            }
            if (exception.getErrorCode() == QuestionErrorCode.STT_RECOGNITION_FAILED) {
                throw new CustomException(RepetitionErrorCode.STT_FAILED);
            }
            throw exception;
        }
    }
}

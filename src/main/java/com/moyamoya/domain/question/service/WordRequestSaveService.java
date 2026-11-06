package com.moyamoya.domain.question.service;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.exception.ChildErrorCode;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.question.dto.response.WordRequestSaveResponse;
import com.moyamoya.domain.question.exception.QuestionErrorCode;
import com.moyamoya.domain.question.session.QuestionSession;
import com.moyamoya.domain.question.session.QuestionSessionStatus;
import com.moyamoya.domain.question.session.QuestionSessionStore;
import com.moyamoya.domain.word.entity.WordRequest;
import com.moyamoya.domain.word.repository.WordRequestRepository;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.s3.S3AudioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WordRequestSaveService {

    private final QuestionSessionStore sessionStore;
    private final QuestionAudioTempService audioTempService;
    private final S3AudioService s3AudioService;
    private final WordRequestRepository wordRequestRepository;
    private final ChildRepository childRepository;
    private final TransactionTemplate transactionTemplate;

    public WordRequestSaveResponse save(
            Long userId,
            String sessionId
    ) {
        if (userId == null) {
            throw new CustomException(QuestionErrorCode.INVALID_QUESTION_REQUEST);
        }

        UUID id = parseSessionId(sessionId);

        QuestionSession session = sessionStore.find(id)
                .orElseThrow(() -> new CustomException(
                        QuestionErrorCode.SESSION_NOT_FOUND
                ));

        synchronized (session) {
            if (!sessionStore.isCurrent(session)) {
                throw new CustomException(
                        QuestionErrorCode.SESSION_NOT_FOUND
                );
            }

            Child child = childRepository.findById(
                    session.getChildId()
            ).orElseThrow(() -> new CustomException(
                    ChildErrorCode.CHILD_NOT_FOUND
            ));

            if (!child.getUser().getId().equals(userId)) {
                throw new CustomException(
                        QuestionErrorCode.ACCESS_FORBIDDEN
                );
            }

            if (session.getStatus()
                    == QuestionSessionStatus.UNRESOLVED) {
                throw new CustomException(
                        QuestionErrorCode.INVALID_SESSION_STATE
                );
            }

            if (session.getStatus()
                    != QuestionSessionStatus.SECOND_CANDIDATES_READY
                    && session.getStatus()
                    != QuestionSessionStatus.SECOND_NO_CANDIDATES) {

                throw new CustomException(
                        QuestionErrorCode.INVALID_SESSION_STATE
                );
            }

            Path tempPath = session.getOriginalAudioTempPath();

            if (tempPath == null) {
                throw new CustomException(
                        QuestionErrorCode.INVALID_SESSION_STATE
                );
            }

            byte[] audioBytes;
            try {
                audioBytes = audioTempService.read(tempPath);
            } catch (RuntimeException exception) {
                throw new CustomException(QuestionErrorCode.WORD_REQUEST_SAVE_FAILED);
            }

            String key = "questions/original/"
                    + session.getChildId()
                    + "/"
                    + session.getSessionId()
                    + ".wav";

            String uploadedKey;
            try {
                uploadedKey = s3AudioService.uploadWav(
                        key,
                        audioBytes
                );
            } catch (RuntimeException exception) {
                throw new CustomException(QuestionErrorCode.WORD_REQUEST_SAVE_FAILED);
            }

            WordRequestSaveResponse response;

            try {
                response = transactionTemplate.execute(status -> {

                    WordRequest request = WordRequest.create(
                            child,
                            session.getTargetPronunciation(),
                            uploadedKey
                    );

                    WordRequest saved =
                            wordRequestRepository.save(request);

                    return WordRequestSaveResponse.from(saved);
                });

                if (response == null) {
                    throw new IllegalStateException(
                            "물어볼 단어 저장 결과가 없습니다."
                    );
                }

            } catch (RuntimeException e) {
                // DB 저장 실패 시 방금 업로드한 음성 삭제
                try {
                    s3AudioService.deleteObject(uploadedKey);
                } catch (RuntimeException cleanupError) {
                    log.error(
                            "DB 저장 실패 후 S3 정리 실패: {}",
                            uploadedKey,
                            cleanupError
                    );
                }
                throw new CustomException(QuestionErrorCode.WORD_REQUEST_SAVE_FAILED);
            }

            try {
                session.markUnresolved();
            } catch (IllegalStateException exception) {
                throw new CustomException(QuestionErrorCode.INVALID_SESSION_STATE);
            }
            sessionStore.save(session);

            audioTempService.delete(tempPath);

            return response;
        }
    }

    private UUID parseSessionId(String sessionId) {
        try {
            return UUID.fromString(sessionId);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CustomException(QuestionErrorCode.INVALID_SESSION_ID);
        }
    }
}

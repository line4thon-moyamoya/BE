package com.moyamoya.domain.question.service;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.exception.ChildErrorCode;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.question.dto.response.ConfirmWordResponse;
import com.moyamoya.domain.question.exception.QuestionErrorCode;
import com.moyamoya.domain.question.model.WordCandidate;
import com.moyamoya.domain.question.session.QuestionSession;
import com.moyamoya.domain.question.session.QuestionSessionStatus;
import com.moyamoya.domain.question.session.QuestionSessionStore;
import com.moyamoya.domain.word.entity.QuestionHistory;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.repository.QuestionHistoryRepository;
import com.moyamoya.domain.word.repository.WordRepository;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.s3.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionConfirmService {

    private final QuestionSessionStore sessionStore;
    private final ChildRepository childRepository;
    private final WordRepository wordRepository;
    private final QuestionHistoryRepository questionHistoryRepository;
    private final CandidateTtsService candidateTtsService;
    private final S3Service s3Service;
    private final TransactionTemplate transactionTemplate;
    private final WordDescriptionService wordDescriptionService;

    public ConfirmWordResponse confirmWord(
            Long userId,
            String sessionId,
            String candidateId
    ) {
        if (userId == null || !StringUtils.hasText(candidateId)) {
            throw new CustomException(
                    QuestionErrorCode.INVALID_QUESTION_REQUEST
            );
        }

        UUID uuid = parseSessionId(sessionId);

        QuestionSession session = sessionStore.find(uuid)
                .orElseThrow(() -> new CustomException(
                        QuestionErrorCode.SESSION_NOT_FOUND
                ));

        Child child = childRepository.findById(session.getChildId())
                .orElseThrow(() -> new CustomException(
                        ChildErrorCode.CHILD_NOT_FOUND
                ));

        if (!child.getUser().getId().equals(userId)) {
            throw new CustomException(
                    QuestionErrorCode.ACCESS_FORBIDDEN
            );
        }

        synchronized (session) {
            if (!sessionStore.isCurrent(session)) {
                throw new CustomException(QuestionErrorCode.SESSION_NOT_FOUND);
            }

            if (session.getStatus() != QuestionSessionStatus.FIRST_CANDIDATES_READY
                    && session.getStatus() != QuestionSessionStatus.SECOND_CANDIDATES_READY) {
                throw new CustomException(
                        QuestionErrorCode.INVALID_SESSION_STATE
                );
            }

            WordCandidate candidate = session
                    .findCurrentCandidate(candidateId)
                    .orElseThrow(() -> new CustomException(
                            QuestionErrorCode.INVALID_CANDIDATE
                    ));

            ConfirmWordResponse response = transactionTemplate.execute(status -> {
                // 기존 단어 재사용 또는 신규 외부 단어 생성
                Word word = wordRepository.findByWord(candidate.word())
                        .orElseGet(() -> createExternalWord(candidate, child));

                // 기존 설명은 유지하고 누락된 연령 설명만 생성, 저장
                fillMissingAgeDescriptions(
                        word,
                        candidate,
                        child.getAgeGroup()
                );

                // 음성이 없는 경우 S3 저장
                // 기존 단어는 이미 저장된 음성 재사용
                try {
                    candidateTtsService.ensureStoredAudio(word);
                } catch (IllegalArgumentException | IllegalStateException e) {
                    throw new CustomException(QuestionErrorCode.TTS_GENERATION_FAILED);
                }

                // 질문 기록 저장 (기본/외부 단어 공통)
                QuestionHistory history = QuestionHistory.create(
                        child,
                        word,
                        session.getTargetPronunciation()
                );
                questionHistoryRepository.save(history);

                String wordAudioUrl;
                String imageUrl;
                try {
                    wordAudioUrl = candidateTtsService.resolveAudioUrl(
                            word.getWordAudioUrl()
                    );

                    imageUrl = candidateTtsService.resolvePresignedUrl(
                            word.getImageUrl()
                    );
                } catch (RuntimeException exception) {
                    throw new CustomException(QuestionErrorCode.TTS_GENERATION_FAILED);
                }

                // 아이 연령에 맞는 쉬운 설명 선택
                String ageDescription = child.getAgeGroup() == AgeGroup.AGE_3_4
                        ? word.getDescAge3To4()
                        : word.getDescAge5();

                return new ConfirmWordResponse(
                        "CONFIRMED",
                        word.getId(),
                        word.getWord(),
                        ageDescription,
                        imageUrl,
                        wordAudioUrl,
                        "WORD_LEARNING"
                );
            });

            if (response == null) {
                throw new CustomException(QuestionErrorCode.INVALID_SESSION_STATE);
            }

            // DB 커밋 성공 후 세션 확정
            session.confirm();
            sessionStore.save(session);

            // 임시 파일 삭제 실패는 로그만 남기고 확정 성공 처리
            try {
                sessionStore.deleteTempAudio(session);
            } catch (RuntimeException e) {
                log.warn("단어 확정 후 임시 음성 삭제 실패. sessionId={}",
                        session.getSessionId(), e);
            }

            return response;
        }
    }

    // 신규 외부 단어만 DB에 생성
    private Word createExternalWord(
            WordCandidate candidate,
            Child child
    ) {
        String definition = candidate.definition();

        if (!StringUtils.hasText(definition)) {
            throw new CustomException(
                    QuestionErrorCode.INVALID_QUESTION_REQUEST
            );
        }

        AgeGroup ageGroup = child.getAgeGroup();

        // 후보 단계에서 생성한 쉬운 설명 재사용
        String easyDescription = candidate.easyDescription();

        // 현재 아이 연령에 해당하는 설명만 우선 설정
        String descAge3To4 =
                ageGroup == AgeGroup.AGE_3_4
                        ? easyDescription
                        : null;

        String descAge5 =
                ageGroup == AgeGroup.AGE_5
                        ? easyDescription
                        : null;

        // 후보 단계에서 생성한 Base64 음성을 S3에 영구 저장
        String wordKey = uploadCandidateAudio(
                candidate.wordAudioBase64(),
                "word"
        );

        // 사전 정의와 쉬운 설명을 분리하여 저장
        Word newWord = Word.createExternal(
                candidate.word(),
                definition,
                null,
                descAge3To4,
                descAge5,
                null,
                null,
                null,
                candidate.imageUrl(),
                wordKey
        );

        return wordRepository.save(newWord);
    }

    // Base64 WAV를 S3에 업로드하고 객체 키 반환
    private String uploadCandidateAudio(
            String audioBase64,
            String type
    ) {
        if (!StringUtils.hasText(audioBase64)) {
            return null;
        }

        byte[] wav;
        try {
            wav = Base64.getDecoder().decode(audioBase64);
        } catch (IllegalArgumentException exception) {
            throw new CustomException(QuestionErrorCode.INVALID_CANDIDATE);
        }

        String key = "words/external/"
                + UUID.randomUUID()
                + "/" + type + ".wav";

        try {
            return s3Service.uploadWav(key, wav);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new CustomException(QuestionErrorCode.TTS_GENERATION_FAILED);
        }
    }

    private UUID parseSessionId(String sessionId) {
        try {
            return UUID.fromString(sessionId);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CustomException(
                    QuestionErrorCode.INVALID_SESSION_ID
            );
        }
    }

    // 현재 아이 연령에 해당하는 설명만 생성 및 저장
    private void fillMissingAgeDescriptions(
            Word word,
            WordCandidate candidate,
            AgeGroup childAgeGroup
    ) {
        String definition = word.getDefinition();

        if (definition == null || definition.isBlank()) {
            return;
        }

        String descAge3To4 = word.getDescAge3To4();
        String descAge5 = word.getDescAge5();

        // 3~4세 아이인 경우
        if (childAgeGroup == AgeGroup.AGE_3_4) {

            if (descAge3To4 == null || descAge3To4.isBlank()) {

                // 후보 단계에서 생성한 설명 재사용
                descAge3To4 = candidate.easyDescription();

                // 후보 설명이 없을 때만 새로 생성
                if (descAge3To4 == null || descAge3To4.isBlank()) {
                    descAge3To4 = wordDescriptionService.generate(
                            word.getWord(),
                            definition,
                            AgeGroup.AGE_3_4
                    );
                }
            }

        // 5세 이상 아이인 경우
        } else if (childAgeGroup == AgeGroup.AGE_5) {

            if (descAge5 == null || descAge5.isBlank()) {

                // 후보 단계에서 생성한 설명 재사용
                descAge5 = candidate.easyDescription();

                // 후보 설명이 없을 때만 새로 생성
                if (descAge5 == null || descAge5.isBlank()) {
                    descAge5 = wordDescriptionService.generate(
                            word.getWord(),
                            definition,
                            AgeGroup.AGE_5
                    );
                }
            }
        }

        // 기존 설명은 유지하고 누락된 값만 저장
        word.fillMissingDescriptions(descAge3To4, descAge5);
    }
}

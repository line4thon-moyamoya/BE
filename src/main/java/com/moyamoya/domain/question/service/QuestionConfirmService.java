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
import com.moyamoya.global.s3.S3AudioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuestionConfirmService {

    private final QuestionSessionStore sessionStore;
    private final ChildRepository childRepository;
    private final WordRepository wordRepository;
    private final QuestionHistoryRepository questionHistoryRepository;
    private final CandidateTtsService candidateTtsService;
    private final S3AudioService s3AudioService;

    @Transactional
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

            // 기존 단어 재사용 또는 신규 외부 단어 생성
            Word word = wordRepository.findByWord(candidate.word())
                    .orElseGet(() -> createExternalWord(candidate, child));

            // 음성이 없는 경우 S3 저장
            // 기존 단어는 이미 저장된 음성 재사용
            try {
                candidateTtsService.ensureStoredAudio(
                        word,
                        child.getAgeGroup()
                );
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

            boolean age3To4 = child.getAgeGroup() == AgeGroup.AGE_3_4;

            String descriptionKey = age3To4
                    ? word.getDescriptionAudioAge3To4Url()
                    : word.getDescriptionAudioAge5Url();

            String wordAudioUrl;
            String descriptionAudioUrl;
            try {
                wordAudioUrl = candidateTtsService.resolveAudioUrl(
                        word.getWordAudioUrl()
                );

                descriptionAudioUrl = candidateTtsService.resolveAudioUrl(
                        descriptionKey
                );
            } catch (RuntimeException exception) {
                throw new CustomException(QuestionErrorCode.TTS_GENERATION_FAILED);
            }

            // 세션 확정
            session.confirm();
            sessionStore.save(session);

            return new ConfirmWordResponse(
                    "CONFIRMED",
                    word.getId(),
                    word.getWord(),
                    candidate.definition(),
                    word.getImageUrl(),
                    wordAudioUrl,
                    descriptionAudioUrl,
                    "WORD_LEARNING"
            );
        }
    }

    // 신규 외부 단어만 DB에 생성
    private Word createExternalWord(
            WordCandidate candidate,
            Child child
    ) {
        String description = candidate.definition();

        if (!StringUtils.hasText(description)) {
            throw new CustomException(
                    QuestionErrorCode.INVALID_QUESTION_REQUEST
            );
        }

        boolean age3To4 = child.getAgeGroup() == AgeGroup.AGE_3_4;

        // 후보 단계에서 만든 Base64를 재사용해 S3 업로드
        String wordKey = uploadCandidateAudio(
                candidate.wordAudioBase64(),
                "word",
                candidate.word()
        );

        String descriptionKey = uploadCandidateAudio(
                candidate.descriptionAudioBase64(),
                age3To4 ? "desc-age-3-4" : "desc-age-5",
                candidate.word()
        );

        // 현재 후보에 없는 상세 메타데이터는 임시로 null 처리
        // songDescription은 NOT NULL이므로 정의를 임시 사용
        Word newWord = Word.createExternal(
                candidate.word(),
                description,
                null,
                age3To4 ? description : null,
                age3To4 ? null : description,
                null,
                null,
                description,
                null,
                candidate.imageUrl(),
                wordKey,
                age3To4 ? descriptionKey : null,
                age3To4 ? null : descriptionKey
        );

        return wordRepository.save(newWord);
    }

    // Base64 WAV를 S3에 업로드하고 객체 키 반환
    private String uploadCandidateAudio(
            String audioBase64,
            String type,
            String word
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
            return s3AudioService.uploadWav(key, wav);
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
}

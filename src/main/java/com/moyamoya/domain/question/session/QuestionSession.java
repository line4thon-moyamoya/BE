package com.moyamoya.domain.question.session;

import com.moyamoya.domain.question.model.WordCandidate;
import com.moyamoya.domain.word.entity.HeardFrom;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.*;

@Getter
public class QuestionSession {

    private final UUID sessionId;
    private final Long childId;

    // CLOVA Speech가 반환한 전체 문장
    private final String originalSentence;

    // 질문 원본 음성 URL (없으면 null)
    private final String audioUrl;

    // F1이 추출한 원본 표현 (조사 제거 전)
    private String recognizedText;

    // 조사 판단으로 생성한 비교용 표현들
    private List<String> targetExpressions = List.of();

    // 1차 및 2차 후보
    private List<WordCandidate> firstCandidates = List.of();
    private List<WordCandidate> secondCandidates = List.of();

    // 1차에서 보여준 단어 (2차에서 제외)
    private Set<String> excludedWords = Set.of();

    // 2차에서 선택한 들은 곳
    private HeardFrom heardFrom;

    private QuestionSessionStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private QuestionSession(
            Long childId,
            String originalSentence,
            String audioUrl
    ) {
        this.sessionId = UUID.randomUUID();
        this.childId = Objects.requireNonNull(childId);
        this.originalSentence = Objects.requireNonNull(originalSentence);
        this.audioUrl = audioUrl;

        this.status = QuestionSessionStatus.PROCESSING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = createdAt;
    }

    public static QuestionSession create(
            Long childId,
            String originalSentence,
            String audioUrl
    ) {
        return new QuestionSession(childId, originalSentence, audioUrl);
    }

    // F1 추출 결과 저장
    public synchronized void setExtractionResult(
            String recognizedText,
            List<String> targetExpressions
    ) {
        requireStatus(QuestionSessionStatus.PROCESSING);

        if (recognizedText == null || recognizedText.isBlank()) {
            throw new IllegalArgumentException("추출된 표현이 없습니다.");
        }

        this.recognizedText = recognizedText;
        this.targetExpressions = List.copyOf(targetExpressions);
        touch();
    }

    // 1차 후보 저장 (후보 0개도 정상 결과)
    public synchronized void setFirstCandidates(
            List<WordCandidate> candidates
    ) {
        requireStatus(QuestionSessionStatus.PROCESSING);

        this.firstCandidates = List.copyOf(candidates);

        this.status = candidates.isEmpty()
                ? QuestionSessionStatus.FIRST_NO_CANDIDATES
                : QuestionSessionStatus.FIRST_CANDIDATES_READY;

        touch();
    }

    // 2차 후보 저장 (1차 후보가 0개여도 진행 가능)
    public synchronized void setSecondCandidates(
            HeardFrom heardFrom,
            List<WordCandidate> candidates
    ) {
        // 1차 탐색이 완료된 상태에서만 2차 탐색 가능
        if (status != QuestionSessionStatus.FIRST_CANDIDATES_READY
                && status != QuestionSessionStatus.FIRST_NO_CANDIDATES) {
            throw new IllegalStateException(
                    "2차 후보를 설정할 수 없는 상태입니다: " + status
            );
        }

        // 1차에서 실제 보여준 단어들을 제외 목록에 저장
        Set<String> excluded = new HashSet<>();

        for (WordCandidate candidate : firstCandidates) {
            excluded.add(candidate.word().trim());
        }

        // 2차 후보에 1차 후보가 다시 등장하면 오류
        for (WordCandidate candidate : candidates) {
            if (excluded.contains(candidate.word().trim())) {
                throw new IllegalArgumentException(
                        "1차에서 보여준 단어를 2차에 다시 제시할 수 없습니다."
                );
            }
        }

        this.heardFrom = heardFrom;
        this.excludedWords = Set.copyOf(excluded);
        this.secondCandidates = List.copyOf(candidates);

        // 후보가 없으면 별도 상태로 구분
        this.status = candidates.isEmpty()
                ? QuestionSessionStatus.SECOND_NO_CANDIDATES
                : QuestionSessionStatus.SECOND_CANDIDATES_READY;

        touch();
    }

    // 현재 단계에서 선택할 수 있는 후보
    public synchronized Optional<WordCandidate> findCurrentCandidate(
            String candidateId
    ) {
        List<WordCandidate> currentCandidates;

        if (status == QuestionSessionStatus.FIRST_CANDIDATES_READY) {
            currentCandidates = firstCandidates;
        } else if (status == QuestionSessionStatus.SECOND_CANDIDATES_READY) {
            currentCandidates = secondCandidates;
        } else {
            return Optional.empty();
        }

        return currentCandidates.stream()
                .filter(candidate -> candidate.candidateId().equals(candidateId))
                .findFirst();
    }

    // 단어 확정 완료 후 호출
    public synchronized void confirm() {
        requireCandidateReady();
        this.status = QuestionSessionStatus.CONFIRMED;
        touch();
    }

    // 2차 후보가 없거나 모두 거절된 경우 미해결 처리
    public synchronized void markUnresolved() {

        if (status != QuestionSessionStatus.SECOND_CANDIDATES_READY
                && status != QuestionSessionStatus.SECOND_NO_CANDIDATES) {
            throw new IllegalStateException(
                    "미해결 처리할 수 없는 상태입니다: " + status
            );
        }

        this.status = QuestionSessionStatus.UNRESOLVED;
        touch();
    }

    public boolean isExpired() {
        return updatedAt.plusMinutes(30).isBefore(LocalDateTime.now());
    }

    private void requireCandidateReady() {
        if (status != QuestionSessionStatus.FIRST_CANDIDATES_READY
                && status != QuestionSessionStatus.SECOND_CANDIDATES_READY) {
            throw new IllegalStateException("후보를 확정할 수 없는 상태입니다.");
        }
    }

    private void requireStatus(QuestionSessionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "잘못된 질문 세션 상태입니다. 현재: " + status
            );
        }
    }

    private void requireStatusIn(QuestionSessionStatus... allowedStatuses) {
        for (QuestionSessionStatus allowedStatus : allowedStatuses) {
            if (status == allowedStatus) {
                return;
            }
        }
        throw new IllegalStateException(
                "잘못된 질문 세션 상태입니다. 현재: " + status
        );
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
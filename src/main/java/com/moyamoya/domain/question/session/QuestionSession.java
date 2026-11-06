package com.moyamoya.domain.question.session;

import com.moyamoya.domain.question.model.WordCandidate;
import com.moyamoya.domain.word.entity.HeardFrom;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.*;
import java.nio.file.Path;

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

    // F2에서 조사 여부를 판단한 발음
    // 확실하지 않으면 recognizedText를 그대로 유지 => 후에 리포트랑 물어볼 단어에 저장
    private String targetPronunciation;

    // 조사 제거 등을 적용한 검색용 표현들
    private List<String> targetExpressions = List.of();

    // 1차 및 2차 후보
    private List<WordCandidate> firstCandidates = List.of();
    private List<WordCandidate> secondCandidates = List.of();
    private Map<String, String> firstPublicCandidateIdMap = Map.of();
    private Map<String, String> secondPublicCandidateIdMap = Map.of();

    // 1차에서 보여준 단어 (2차에서 제외)
    private Set<String> excludedWords = Set.of();

    // 2차에서 선택한 들은 곳
    private HeardFrom heardFrom;

    // S3 업로드 전까지 보관할 원본 음성 임시 파일
    private Path originalAudioTempPath;

    // 원본 파일의 MIME 타입
    private String originalAudioContentType;

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

        if (targetExpressions == null || targetExpressions.isEmpty()) {
            throw new IllegalArgumentException("검색용 표현이 없습니다.");
        }

        this.recognizedText = recognizedText.trim();

        // F2 판단 전에는 원본 표현 유지
        this.targetPronunciation = this.recognizedText;

        this.targetExpressions = List.copyOf(targetExpressions);
        touch();
    }

    // F2에서 조사 여부를 판단한 후 저장할 발음을 갱신
    public synchronized void updateTargetPronunciation(
            String pronunciation
    ) {
        // 질문 처리 중 또는 후보 탐색이 끝난 상태에서만 수정 가능
        requireStatusIn(
                QuestionSessionStatus.PROCESSING,
                QuestionSessionStatus.FIRST_CANDIDATES_READY,
                QuestionSessionStatus.FIRST_NO_CANDIDATES,
                QuestionSessionStatus.SECOND_CANDIDATES_READY,
                QuestionSessionStatus.SECOND_NO_CANDIDATES
        );

        if (recognizedText == null) {
            throw new IllegalStateException(
                    "F1 추출 결과가 먼저 저장되어야 합니다."
            );
        }

        if (pronunciation == null || pronunciation.isBlank()) {
            throw new IllegalArgumentException(
                    "저장할 발음이 없습니다."
            );
        }

        String normalized = pronunciation.trim();

        // F2에서 생성한 표현 중 하나만 선택 가능
        if (!targetExpressions.contains(normalized)) {
            throw new IllegalArgumentException(
                    "검색용 표현 목록에 없는 발음입니다."
            );
        }

        this.targetPronunciation = normalized;
        touch();
    }

    // 1차 후보 저장 (후보 0개도 정상 결과)
    public synchronized void setFirstCandidates(
            List<WordCandidate> candidates
    ) {
        requireStatus(QuestionSessionStatus.PROCESSING);

        this.firstCandidates = List.copyOf(candidates);
        this.firstPublicCandidateIdMap = buildPublicCandidateIdMap(this.firstCandidates);
        this.secondPublicCandidateIdMap = Map.of();

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
        this.secondPublicCandidateIdMap = buildPublicCandidateIdMap(this.secondCandidates);

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
        Map<String, String> currentPublicCandidateIdMap;

        if (status == QuestionSessionStatus.FIRST_CANDIDATES_READY) {
            currentCandidates = firstCandidates;
            currentPublicCandidateIdMap = firstPublicCandidateIdMap;
        } else if (status == QuestionSessionStatus.SECOND_CANDIDATES_READY) {
            currentCandidates = secondCandidates;
            currentPublicCandidateIdMap = secondPublicCandidateIdMap;
        } else {
            return Optional.empty();
        }

        String internalCandidateId = currentPublicCandidateIdMap.getOrDefault(
                candidateId,
                candidateId
        );

        return currentCandidates.stream()
                .filter(candidate -> candidate.candidateId().equals(internalCandidateId))
                .findFirst();
    }

    public synchronized List<WordCandidate> getFirstCandidatesForClient() {
        return toClientCandidates(firstCandidates, firstPublicCandidateIdMap);
    }

    public synchronized List<WordCandidate> getSecondCandidatesForClient() {
        return toClientCandidates(secondCandidates, secondPublicCandidateIdMap);
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

    // STT 분석에 사용한 원본 음성 연결
    public synchronized void attachOriginalAudio(
            Path tempPath,
            String contentType
    ) {
        requireStatus(QuestionSessionStatus.PROCESSING);

        if (originalAudioTempPath != null) {
            throw new IllegalStateException(
                    "원본 음성이 이미 연결되어 있습니다."
            );
        }

        this.originalAudioTempPath =
                Objects.requireNonNull(tempPath);

        this.originalAudioContentType = contentType;
        touch();
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

    private Map<String, String> buildPublicCandidateIdMap(List<WordCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Map.of();
        }

        Map<String, String> mapping = new LinkedHashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            String publicId = "c_%03d".formatted(i + 1);
            mapping.put(publicId, candidates.get(i).candidateId());
        }
        return Map.copyOf(mapping);
    }

    private List<WordCandidate> toClientCandidates(
            List<WordCandidate> internalCandidates,
            Map<String, String> publicCandidateIdMap
    ) {
        if (internalCandidates == null || internalCandidates.isEmpty()) {
            return List.of();
        }

        Map<String, String> internalToPublicMap = new HashMap<>();
        publicCandidateIdMap.forEach((publicId, internalId) -> internalToPublicMap.put(internalId, publicId));

        return internalCandidates.stream()
                .map(candidate -> new WordCandidate(
                        internalToPublicMap.getOrDefault(candidate.candidateId(), candidate.candidateId()),
                        candidate.wordId(),
                        candidate.word(),
                        candidate.sourceType(),
                        candidate.pronunciationScore(),
                        candidate.contextScore(),
                        candidate.finalScore(),
                        candidate.definition(),
                        candidate.imageUrl(),
                        candidate.wordAudioUrl(),
                        candidate.descriptionAudioUrl(),
                        candidate.wordAudioBase64(),
                        candidate.descriptionAudioBase64()
                ))
                .toList();
    }
}
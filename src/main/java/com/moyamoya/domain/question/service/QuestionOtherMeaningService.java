// 사전 검색 결과 + 상세 조회 => 추가 뜻 반환
package com.moyamoya.domain.question.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.exception.ChildErrorCode;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.question.dto.response.OtherMeaningsResponse;
import com.moyamoya.domain.question.exception.QuestionErrorCode;
import com.moyamoya.domain.question.model.DictionaryEntry;
import com.moyamoya.domain.question.model.OtherMeaning;
import com.moyamoya.domain.question.model.WordCandidate;
import com.moyamoya.domain.question.session.QuestionSession;
import com.moyamoya.domain.question.session.QuestionSessionStatus;
import com.moyamoya.domain.question.session.QuestionSessionStore;
import com.moyamoya.domain.word.repository.WordRepository;
import com.moyamoya.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionOtherMeaningService {

    private final QuestionSessionStore sessionStore;
    private final ChildRepository childRepository;
    private final ExternalDictionaryService dictionaryService;
    private final WordRepository wordRepository;
    private final OpenAiClient openAiClient;
    private final WordDescriptionService descriptionService;
    private final ObjectMapper objectMapper;
    private final BadWordFilterService badWordFilterService;

    public OtherMeaningsResponse getOtherMeanings(
            Long userId,
            String sessionId,
            String candidateId
    ) {
        // 1. 세션 조회
        UUID uuid;
        try {
            uuid = UUID.fromString(sessionId);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CustomException(
                    QuestionErrorCode.INVALID_SESSION_ID
            );
        }

        QuestionSession session = sessionStore.find(uuid)
                .orElseThrow(() -> new CustomException(
                        QuestionErrorCode.SESSION_NOT_FOUND
                ));

        // 2. 사용자 권한 검증
        if (userId == null) {
            throw new CustomException(
                    QuestionErrorCode.INVALID_QUESTION_REQUEST
            );
        }

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
                throw new CustomException(
                        QuestionErrorCode.SESSION_NOT_FOUND
                );
            }

            // 3. F1/F2 후보 준비 상태인지 확인
            boolean first = session.getStatus()
                    == QuestionSessionStatus.FIRST_CANDIDATES_READY;

            boolean second = session.getStatus()
                    == QuestionSessionStatus.SECOND_CANDIDATES_READY;

            if (!first && !second) {
                throw new CustomException(
                        QuestionErrorCode.INVALID_SESSION_STATE
                );
            }

            List<WordCandidate> currentCandidates = first
                    ? session.getFirstCandidates()
                    : session.getSecondCandidates();

            WordCandidate candidate = session
                    .findCurrentCandidate(candidateId)
                    .orElseThrow(() -> new CustomException(
                            QuestionErrorCode.INVALID_CANDIDATE
                    ));

            // 4. 신규 외부 후보 1개인 경우만 허용
            boolean allowed =
                    currentCandidates.size() == 1
                            && candidate.wordId() == null
                            && candidate.sourceType() != null
                            && "EXTERNAL".equals(candidate.sourceType().name());

            if (!allowed) {
                throw new CustomException(
                        QuestionErrorCode.OTHER_MEANINGS_NOT_ALLOWED
                );
            }

            if (wordRepository.existsByWord(candidate.word())) {
                throw new CustomException(
                        QuestionErrorCode.OTHER_MEANINGS_NOT_ALLOWED
                );
            }

            // 5. 캐시가 있으면 재사용
            if (session.hasCachedOtherMeanings(candidateId)) {
                return buildResponse(
                        session,
                        candidateId,
                        candidate.word(),
                        session.getCachedOtherMeanings(candidateId)
                );
            }

            // 6. 사전에서 같은 단어의 뜻 조회
            List<DictionaryEntry> entries;
            try {
                entries = dictionaryService.searchAllMeaningsStrict(
                        candidate.word()
                );
            } catch (RuntimeException e) {
                log.warn("다른 뜻 사전 조회 실패: word={}",
                        candidate.word(), e);

                throw new CustomException(
                        QuestionErrorCode.DICTIONARY_LOOKUP_FAILED
                );
            }

            // 7. 현재 제시된 뜻 및 금칙어가 포함된 뜻 제외
            List<DictionaryEntry> alternatives = entries.stream()
                    .filter(entry -> StringUtils.hasText(entry.definition()))
                    .filter(entry -> !normalize(entry.definition())
                            .equals(normalize(candidate.definition())))
                    .filter(entry -> !badWordFilterService
                            .containsBlockedText(entry.definition()))
                    .distinct()
                    .toList();

            // 8. LLM으로 최대 2개 선정
            List<DictionaryEntry> selected = selectMeanings(
                    candidate.word(),
                    alternatives
            );

            // 9. 아이 연령별 쉬운 설명 생성
            List<OtherMeaning> meanings = new ArrayList<>();

            for (DictionaryEntry entry : selected) {

                String description = descriptionService.generate(
                        candidate.word(),
                        entry.definition(),
                        child.getAgeGroup()
                );

                // 설명 생성에 실패하면 해당 뜻은 제외
                if (!StringUtils.hasText(description)) {
                    continue;
                }

                String meaningId = "m_%03d".formatted(
                        meanings.size() + 1
                );

                meanings.add(new OtherMeaning(
                        meaningId,
                        entry.definition(),
                        description
                ));
            }

            // 10. 세션에 저장 (0개인 경우도 캐시)
            session.cacheOtherMeanings(candidateId, meanings);
            sessionStore.save(session);

            return buildResponse(
                    session,
                    candidateId,
                    candidate.word(),
                    meanings
            );
        }
    }

    // LLM은 사전 정의의 ID만 선택하도록 제한
    private List<DictionaryEntry> selectMeanings(
            String word,
            List<DictionaryEntry> entries
    ) {
        if (entries.isEmpty()) {
            return List.of();
        }

        StringBuilder input = new StringBuilder();
        input.append("단어: ").append(word).append("\n\n");

        for (int i = 0; i < entries.size(); i++) {
            input.append(i + 1)
                    .append(". ")
                    .append(entries.get(i).definition())
                    .append("\n");
        }

        String instructions = """
                당신은 어린이 국어 학습 서비스의 뜻 선정 담당자입니다.

                주어진 사전 정의 중 3~8세 어린이가
                일상에서 접할 가능성이 높은 뜻을
                최대 2개 선정하세요.

                규칙:
                - 반드시 제공된 사전 정의에서만 선택
                - 새로운 뜻을 만들지 말 것
                - 기존 뜻과 유사하거나 중복되는 뜻은 제외
                - 전문 용어, 고어, 비속어, 성인용 의미는 제외
                - 적합한 뜻이 없다면 빈 배열 반환
                - JSON 객체만 반환하고 설명은 작성하지 말 것

                출력 형식:
                {"selectedIds":[1,2]}
                """;

        String result = openAiClient.generateText(
                instructions,
                input.toString()
        );

        if (!StringUtils.hasText(result)) {
            log.warn("다른 뜻 선정 LLM 응답 없음: word={}", word);
            return List.of();
        }

        try {
            String json = result.trim();

            if (json.startsWith("```")) {
                json = json.replaceFirst("^```(?:json)?\\s*", "")
                        .replaceFirst("\\s*```$", "");
            }

            JsonNode root = objectMapper.readTree(json);
            JsonNode ids = root.path("selectedIds");

            if (!ids.isArray()) {
                return List.of();
            }

            List<DictionaryEntry> selected = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();

            for (JsonNode idNode : ids) {
                if (!idNode.isIntegralNumber()) {
                    continue;
                }

                int index = idNode.asInt() - 1;

                if (index < 0 || index >= entries.size()) {
                    continue;
                }

                if (seen.add(index)) {
                    selected.add(entries.get(index));
                }

                if (selected.size() == 2) {
                    break;
                }
            }

            return selected;

        } catch (Exception e) {
            log.warn("다른 뜻 선정 결과 파싱 실패: word={}", word, e);
            return List.of();
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.replaceAll("\\s+", "")
                .replaceAll("[.,!?。]", "")
                .trim();
    }

    private OtherMeaningsResponse buildResponse(
            QuestionSession session,
            String candidateId,
            String word,
            List<OtherMeaning> meanings
    ) {
        List<OtherMeaningsResponse.Meaning> responses =
                meanings.stream()
                        .map(meaning ->
                                new OtherMeaningsResponse.Meaning(
                                        meaning.meaningId(),
                                        meaning.description()
                                )
                        )
                        .toList();

        return OtherMeaningsResponse.of(
                session.getSessionId(),
                candidateId,
                word,
                responses
        );
    }
}

// 질문 기록 집계, 단어 및 미해결 요청 분류
package com.moyamoya.domain.report.service;

import com.moyamoya.domain.report.dto.WeeklyReportSnapshot;
import com.moyamoya.domain.report.dto.response.WeeklyReportResponse.LearnedWord;
import com.moyamoya.domain.report.dto.response.WeeklyReportResponse.UnrecognizedWord;
import com.moyamoya.domain.word.entity.QuestionHistory;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.entity.WordRequest;
import com.moyamoya.domain.word.entity.WordRequestStatus;
import com.moyamoya.domain.word.repository.QuestionHistoryRepository;
import com.moyamoya.domain.word.repository.WordRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class WeeklyReportAggregationService {

    private final QuestionHistoryRepository questionHistoryRepository;
    private final WordRequestRepository wordRequestRepository;

    // 월요일 00:00 ~ 다음 월요일 00:00 미만
    @Transactional(readOnly = true)
    public WeeklyReportSnapshot aggregate(
            Long childId,
            LocalDate weekStartDate,
            LocalDateTime snapshotAt
    ) {
        return aggregateRange(
                childId,
                weekStartDate.atStartOfDay(),
                weekStartDate.plusWeeks(1).atStartOfDay(),
                snapshotAt
        );
    }

    // 지정한 기간의 질문 기록 집계 (테스트 미리보기에서도 사용)
    @Transactional(readOnly = true)
    public WeeklyReportSnapshot aggregateRange(
            Long childId,
            LocalDateTime start,
            LocalDateTime end,
            LocalDateTime snapshotAt
    ) {

        // 1. 해당 기간에 생성된 질문 기록 조회
        List<QuestionHistory> histories =
                questionHistoryRepository
                        .findByChildIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                                childId, start, end
                        );

        // 2. 해당 기간에 생성된 못 찾은 말 요청 조회
        List<WordRequest> requests =
                wordRequestRepository
                        .findByChildIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                                childId, start, end
                        );

        // 3. 전체 질문 횟수 (중복 질문 및 해결된 요청 포함)
        int questionCount = histories.size() + requests.size();

        // 4. QuestionHistory를 wordId 기준으로 그룹화
        Map<Long, WordGroup> groupedWords = new LinkedHashMap<>();

        for (QuestionHistory history : histories) {
            Word word = history.getWord();

            WordGroup group = groupedWords.computeIfAbsent(
                    word.getId(),
                    id -> new WordGroup(
                            word.getId(),
                            word.getWord(),
                            word.getImageUrl()
                    )
            );

            // 같은 단어를 여러 번 질문했다면 인식 원문을 모두 저장
            if (history.getRecognizedText() != null) {
                group.recognizedTexts.add(history.getRecognizedText());
            }
        }

        // 5. 확정된 단어 기록 생성
        List<LearnedWord> words = new ArrayList<>();

        for (WordGroup group : groupedWords.values()) {
            words.add(new LearnedWord(
                    group.wordId,
                    group.word,
                    new ArrayList<>(group.recognizedTexts),
                    group.imageUrl // S3 객체 키
            ));
        }

        // 6. WordRequest를 해결 여부에 따라 분류
        List<UnrecognizedWord> unrecognizedWords = new ArrayList<>();

        for (WordRequest request : requests) {

            // 스냅샷 생성 시점까지 해결된 요청인지 확인
            boolean resolvedAtSnapshot =
                    request.getStatus() == WordRequestStatus.RESOLVED
                            && request.getResolvedAt() != null
                            && !request.getResolvedAt().isAfter(snapshotAt);

            if (resolvedAtSnapshot) {

                // 수정 단어가 없는 경우 해당 단어만 제외
                if (!StringUtils.hasText(request.getCorrectedWord())) {
                    log.warn(
                            "해결된 요청의 correctedWord 누락 - wordRequestId={}",
                            request.getId()
                    );
                    continue;
                }

                // 해결된 요청은 words에 포함
                words.add(new LearnedWord(
                        null,
                        request.getCorrectedWord().trim(),
                        recognizedTextsOf(request.getRecognizedText()),
                        null
                ));

            } else {

                // 미해결 요청만 못 찾은 말 목록에 포함
                unrecognizedWords.add(
                        new UnrecognizedWord(request.getRecognizedText())
                );
            }
        }

        // 7. 미해결 요청 개수
        int unrecognizedCount = unrecognizedWords.size();

        // 8. JSON 스냅샷 반환
        return new WeeklyReportSnapshot(
                questionCount,
                unrecognizedCount,
                List.copyOf(unrecognizedWords),
                List.copyOf(words),
                null
        );
    }

    // 해결된 WordRequest의 질문 원문을 리스트로 변환
    private List<String> recognizedTextsOf(String recognizedText) {
        if (recognizedText == null) {
            return List.of();
        }
        return List.of(recognizedText);
    }

    // 동일 wordId의 질문 기록을 묶기 위한 내부 클래스
    private static class WordGroup {

        private final Long wordId;
        private final String word;
        private final String imageUrl;
        private final List<String> recognizedTexts = new ArrayList<>();

        private WordGroup(
                Long wordId,
                String word,
                String imageUrl
        ) {
            this.wordId = wordId;
            this.word = word;
            this.imageUrl = imageUrl;
        }
    }
}

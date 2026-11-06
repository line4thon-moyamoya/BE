// 조사 포함해서 질문에서 표현 추출
package com.moyamoya.domain.question.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.moyamoya.domain.question.config.QuestionDataLoader;
import com.moyamoya.domain.question.model.QuestionExtractionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class QuestionTargetExtractor {

    private final QuestionDataLoader dataLoader;

    // 설정에 없는 경우 사용할 기본 호칭 패턴
    private static final Pattern DEFAULT_VOCATIVE =
            Pattern.compile("^(?:엄마|아빠|선생님|모야)(?:야|아)?[,，]?\\s+");

    // "경제"라는 게 뭐야? 형태
    private static final Pattern QUOTED_TARGET =
            Pattern.compile("[\"'“‘]([^\"'”’]+)[\"'”’]");

    // "구케이원이라고 했는데 그게 뭐야?" 형태
    private static final Pattern REPORTED_SPEECH =
            Pattern.compile("([가-힣]+)(?:이라고|라고)\\s*(?:했는데|하던데|했어)");

    public QuestionExtractionResult extract(String originalSentence) {

        if (originalSentence == null || originalSentence.isBlank()) {
            return QuestionExtractionResult.failure(
                    originalSentence, "EMPTY_SENTENCE"
            );
        }

        String sentence = normalize(originalSentence);
        sentence = removeVocative(sentence);

        // 질문 표현 정규화
        sentence = normalizeQuestionExpression(sentence);

        if (sentence.isBlank()) {
            return QuestionExtractionResult.failure(
                    originalSentence, "TARGET_NOT_FOUND"
            );
        }

        // 1. 인용된 표현이 질문 대상이면 우선 추출
        String quoted = extractQuotedTarget(sentence);

        if (quoted != null) {
            return successOrFailure(originalSentence, quoted);
        }

        // 2. "아빠가 구케이원이라고 했는데..."에서 전달된 표현 추출
        String reported = extractReportedSpeech(sentence);

        if (reported != null) {
            return successOrFailure(originalSentence, reported);
        }

        // 3. "경제라는 게 뭐야" 형태
        String raneun = extractRaneun(sentence);

        if (raneun != null) {
            return successOrFailure(originalSentence, raneun);
        }

        // 4. 설정 파일에 정의된 질문 꼬리 제거
        String target = removeQuestionTail(sentence);

        // 5. 꼬리가 없으면 전체 표현을 F2로 전달
        if (target == null) {
            target = sentence;
        }

        return successOrFailure(originalSentence, target);
    }

    // 질문 표현만 정규화 (목표 단어의 발음은 변경하지 않음)
    private String normalizeQuestionExpression(String sentence) {
        return sentence
                .replaceAll("(?<=\\s)머야$", "뭐야")
                .replaceAll("(?<=\\s)모야$", "뭐야")
                .replaceAll("(?<=\\s)뭘까$", "뭐야")
                .replaceAll("(?<=\\s)뭔데$", "뭐야");
    }

    private QuestionExtractionResult successOrFailure(
            String originalSentence,
            String target
    ) {
        target = normalize(target);

        if (target.isBlank()) {
            return QuestionExtractionResult.failure(
                    originalSentence, "TARGET_NOT_FOUND"
            );
        }

        // "이거", "그게" 등 지시어만 있으면 단어 특정 불가
        if (isPointingOnly(target)) {
            return QuestionExtractionResult.failure(
                    originalSentence, "POINTING_ONLY"
            );
        }

        return QuestionExtractionResult.success(
                originalSentence,
                target
        );
    }

    private String normalize(String text) {
        return text.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[?？!！。.,，]+$", "")
                .trim();
    }

    private String removeVocative(String sentence) {
        String regex = dataLoader.getConfig()
                .path("f1")
                .path("vocative_regex")
                .asText("");

        Pattern pattern = regex.isBlank()
                ? DEFAULT_VOCATIVE
                : Pattern.compile(regex);

        return pattern.matcher(sentence)
                .replaceFirst("")
                .trim();
    }

    private String extractQuotedTarget(String sentence) {
        Matcher matcher = QUOTED_TARGET.matcher(sentence);

        if (!matcher.find()) {
            return null;
        }

        // 단순 인용문이 아니라 의미를 묻는 문장인지 확인
        String afterQuote = sentence.substring(matcher.end());

        // 인용된 단어의 의미를 묻는 질문인지 확인
        if (afterQuote.matches(
                ".*(?:뭐야|머야|모야|뭘까|뭘까요|뭔데|뭐지|뭐예요|뭐에요|뜻|의미|알려줘|설명해줘).*"
        )) {
            return matcher.group(1).trim();
        }

        return null;
    }

    private String extractReportedSpeech(String sentence) {
        Matcher matcher = REPORTED_SPEECH.matcher(sentence);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private String extractRaneun(String sentence) {
        JsonNode f1 = dataLoader.getConfig().path("f1");
        String regex = f1.path("raneun_regex").asText("");

        if (regex.isBlank()) {
            return null;
        }

        Matcher matcher = Pattern.compile(regex).matcher(sentence);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private String removeQuestionTail(String sentence) {
        JsonNode tailsNode = dataLoader.getConfig()
                .path("f1")
                .path("tails_longest_first");

        List<String> tails = new ArrayList<>();

        for (JsonNode node : tailsNode) {
            tails.add(node.asText());
        }

        tails.sort(
                Comparator.comparingInt(String::length).reversed()
        );

        for (String tail : tails) {
            if (sentence.endsWith(tail)) {
                return sentence.substring(
                        0,
                        sentence.length() - tail.length()
                ).trim();
            }
        }

        return null;
    }

    private boolean isPointingOnly(String target) {
        JsonNode pointingWords = dataLoader.getConfig()
                .path("f1")
                .path("pointing_words");

        for (JsonNode word : pointingWords) {
            if (target.equals(word.asText())) {
                return true;
            }
        }

        return target.equals("이것")
                || target.equals("저것")
                || target.equals("그것");
    }
}


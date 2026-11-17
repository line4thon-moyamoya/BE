package com.moyamoya.domain.question.service;

import com.moyamoya.domain.question.config.QuestionDataLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BadWordFilterService {

    private final QuestionDataLoader dataLoader;

    public boolean isBlockedWord(String word) {
        String normalized = normalize(word);

        if (normalized.isBlank()) {
            return false;
        }

        // 금칙어 완전 일치는 무조건 차단
        return dataLoader.getBadwords().stream()
                .map(this::normalize)
                .anyMatch(normalized::equals);
    }

    public boolean containsBlockedText(String text) {

        if (text == null || text.isBlank()) {
            return false;
        }

        String normalized = normalize(text);

        // 토큰 분할 전 원문 전체에서 등록된 금칙어 표현을 우선 검사
        boolean blockedInWholeText = dataLoader.getBadwords()
                .stream()
                .map(this::normalize)
                .filter(word -> !word.isBlank())
                .anyMatch(normalized::contains);

        if (blockedInWholeText) {
            return true;
        }

        String[] tokens = normalized.split(
                "[\\s\\p{P}\\p{Z}]+"
        );

        for (String token : tokens) {

            if (token.isBlank()) {
                continue;
            }

            if (isBlockedWord(token)) {
                return true;
            }

            // 검수된 정상 단어는 부분 일치 검사 제외
            if (containsCsvWord(token)) {
                continue;
            }

            boolean blocked = dataLoader.getBadwords()
                    .stream()
                    .map(this::normalize)
                    .filter(word -> word.length() >= 3)
                    .anyMatch(token::contains);

            if (blocked) {
                return true;
            }
        }

        return false;
    }

    private String normalize(String text) {

        if (text == null) {
            return "";
        }

        return Normalizer.normalize(
                        text, Normalizer.Form.NFKC
                )
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private boolean containsCsvWord(String word) {
        if (word == null || word.isBlank()) {
            return false;
        }

        return dataLoader.getWords().stream()
                .anyMatch(data ->
                        data.word().equalsIgnoreCase(word.trim())
                );
    }
}
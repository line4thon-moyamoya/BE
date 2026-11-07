package com.moyamoya.domain.word.service;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.question.config.QuestionDataLoader;
import com.moyamoya.domain.question.model.QuestionWordData;
import com.moyamoya.domain.question.service.PronunciationSimilarity;
import com.moyamoya.domain.word.model.RepetitionEvaluation;
import com.moyamoya.domain.word.model.RepetitionEvaluation.Status;
import com.moyamoya.domain.word.repository.WordRepository;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RepetitionEvaluationService {
    private static final double SUCCESS_THRESHOLD = 0.8;

    private final PronunciationSimilarity pronunciationSimilarity;
    private final QuestionDataLoader questionDataLoader;
    private final WordRepository wordRepository;

    // STT로 변환된 텍스트를 기준으로 비교
    @Transactional(readOnly = true)
    public RepetitionEvaluation evaluate(String recognizedText, String targetWord, AgeGroup ageGroup) {
        String target = normalize(targetWord);
        if (target.isEmpty() || ageGroup == null) {
            throw new IllegalArgumentException("대상 단어와 아이 연령대가 필요합니다.");
        }
        String spoken = normalize(recognizedText);
        if (spoken.isEmpty()) {
            return new RepetitionEvaluation(Status.NO_SPEECH, 0.0);
        }
        if (spoken.equals(target)) {
            return new RepetitionEvaluation(Status.SUCCESS, 1.0);
        }

        List<QuestionWordData> dictionary = questionDataLoader.getWords();
        Set<String> accepted = new LinkedHashSet<>();
        accepted.add(target);
        dictionary.stream().filter(data -> normalize(data.word()).equals(target)).findFirst()
                .ifPresent(data -> {
                    addPronunciations(accepted, data.pronExpected());
                    if (ageGroup == AgeGroup.AGE_3_4) {
                        addPronunciations(accepted, data.pronActualAge3());
                    }
                });
        if (accepted.contains(spoken)) {
            return new RepetitionEvaluation(Status.SUCCESS, 1.0);
        }

        double score = accepted.stream()
                .mapToDouble(pronunciation -> pronunciationSimilarity.calculate(spoken, pronunciation))
                .max().orElse(0.0);
        boolean anotherWord = dictionary.stream().anyMatch(data -> normalize(data.word()).equals(spoken))
                || wordRepository.existsByWord(spoken);
        Status status = !anotherWord && score >= SUCCESS_THRESHOLD ? Status.SUCCESS : Status.FAILURE;
        return new RepetitionEvaluation(status, score);
    }

    private void addPronunciations(Set<String> accepted, String raw) {
        if (raw == null) {
            return;
        }
        for (String pronunciation : raw.split("[,;|]")) {
            String normalized = normalize(pronunciation);
            if (!normalized.isEmpty()) {
                accepted.add(normalized);
            }
        }
    }

    private String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFC)
                .replaceAll("[\\p{P}\\p{Z}\\s]+", "");
    }
}

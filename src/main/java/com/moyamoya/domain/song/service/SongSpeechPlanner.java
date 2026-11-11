package com.moyamoya.domain.song.service;

import com.moyamoya.domain.song.model.SongSpeechPlacement;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SongSpeechPlanner {
    private static final List<Double> WORD_START_SECONDS = List.of(12.0, 16.0, 20.0, 24.0, 28.0);

    public List<SongSpeechPlacement> plan(List<String> words) {
        if (words == null || words.size() != WORD_START_SECONDS.size()) {
            throw new IllegalArgumentException("단어 5개가 필요합니다.");
        }
        var placements = new ArrayList<SongSpeechPlacement>();
        for (int index = 0; index < words.size(); index++) {
            String word = words.get(index);
            if (word == null || word.isBlank() || word.length() > 100) {
                throw new IllegalArgumentException("단어는 1~100자여야 합니다.");
            }
            word = word.strip();
            List<String> syllables = word.codePoints().mapToObj(Character::toString).toList();
            double start = WORD_START_SECONDS.get(index);
            double[] offsets = switch (syllables.size()) {
                case 1 -> new double[]{0.0, 0.99};
                case 2, 4 -> new double[]{0.0, 0.50, 0.99, 1.49};
                case 3 -> new double[]{0.0, 0.50, 0.99};
                case 5 -> new double[]{0.0, 0.25, 0.50, 0.74, 0.99};
                default -> new double[]{0.0};
            };
            for (int slot = 0; slot < offsets.length; slot++) {
                String text = syllables.size() >= 6 ? word : syllables.get(slot % syllables.size());
                placements.add(new SongSpeechPlacement(text, start + offsets[slot]));
            }
        }
        return List.copyOf(placements);
    }
}

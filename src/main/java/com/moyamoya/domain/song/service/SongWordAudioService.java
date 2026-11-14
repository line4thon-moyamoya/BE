package com.moyamoya.domain.song.service;

import com.moyamoya.domain.song.model.SongAudioPlacement;
import com.moyamoya.domain.song.repository.SongWordAudioRepository;
import com.moyamoya.domain.word.entity.Word;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongWordAudioService {
    record Voice(byte[] audio, boolean cacheable) {}

    private final SongWordAudioRepository words;
    private final SongSpeechPlanner planner;
    private final SongTtsService tts;
    private final PythonSongMixerClient mixer;
    private final SongAudioStorageService storage;
    private final Object[] locks = IntStream.range(0, 64).mapToObj(index -> new Object()).toArray();

    public void prepare(String text) {
        synchronized (locks[Math.floorMod(text.hashCode(), locks.length)]) {
            var word = words.findByWord(text).orElse(null);
            if (word == null || storage.isCurrentWordKey(word.getId(), text, word.getSongAudioUrl())) {
                return;
            }
            var metrics = new SongGenerationMetrics();
            metrics.cacheMiss();
            create(text, word, metrics, new LinkedHashMap<>());
        }
    }

    byte[] getOrCreate(String text, SongGenerationMetrics metrics, Map<String, Voice> syllables) {
        synchronized (locks[Math.floorMod(text.hashCode(), locks.length)]) {
            return loadOrCreate(text, metrics, syllables);
        }
    }

    private byte[] loadOrCreate(String text, SongGenerationMetrics metrics, Map<String, Voice> syllables) {
        var word = words.findByWord(text).orElse(null);
        String previous = word == null ? null : word.getSongAudioUrl();
        if (word != null && storage.isCurrentWordKey(word.getId(), text, previous)) {
            byte[] cached = metrics.measure(SongGenerationMetrics.Stage.WORD_DOWNLOAD,
                    () -> storage.downloadWord(previous));
            if (cached != null) {
                metrics.cacheHit();
                return cached;
            }
        }
        metrics.cacheMiss();
        return create(text, word, metrics, syllables);
    }

    private byte[] create(String text, Word word, SongGenerationMetrics metrics, Map<String, Voice> syllables) {
        String previous = word == null ? null : word.getSongAudioUrl();
        var plan = planner.planWord(text);
        var indexes = new LinkedHashMap<String, Integer>();
        var voices = new ArrayList<byte[]>();
        var placements = new ArrayList<SongAudioPlacement>();
        boolean cacheable = true;
        long totalBytes = 0;
        for (int index = 0; index < plan.size(); index++) {
            var speech = plan.get(index);
            Integer audioIndex = indexes.get(speech.text());
            if (audioIndex == null) {
                Voice voice = syllables.computeIfAbsent(speech.text(), key -> {
                    int exhaustedBefore = metrics.exhaustedInputs();
                    byte[] audio = tts.synthesize(key, metrics);
                    return new Voice(audio, exhaustedBefore == metrics.exhaustedInputs());
                });
                if (voice.audio() == null || voice.audio().length == 0 || voice.audio().length > 10 * 1024 * 1024) {
                    throw new IllegalStateException("단어 TTS 음성 데이터가 올바르지 않습니다.");
                }
                totalBytes += voice.audio().length;
                if (totalBytes > 50L * 1024 * 1024) {
                    throw new IllegalStateException("단어 노래 음원 요청 크기가 50MB를 초과했습니다.");
                }
                cacheable &= voice.cacheable();
                audioIndex = voices.size();
                indexes.put(speech.text(), audioIndex);
                voices.add(voice.audio());
            }
            double next = index + 1 < plan.size() ? plan.get(index + 1).startSec() : SongSpeechPlanner.WORD_SLOT_SECONDS;
            Double available = text.codePointCount(0, text.length()) >= 6 ? null
                    : Math.rint((next - speech.startSec()) * 1000) / 1000;
            placements.add(new SongAudioPlacement(audioIndex, speech.startSec(), available));
        }
        byte[] clip = metrics.measure(SongGenerationMetrics.Stage.MIXING, () -> mixer.composeWord(voices, placements));
        if (clip == null || clip.length == 0 || clip.length > 10 * 1024 * 1024) {
            throw new IllegalStateException("단어 노래 음원 크기가 올바르지 않습니다.");
        }
        if (word == null || !cacheable) {
            return clip;
        }
        String key = metrics.measure(SongGenerationMetrics.Stage.WORD_UPLOAD,
                () -> storage.uploadWord(word.getId(), text, clip));
        try {
            if (words.replaceAudioKey(word.getId(), text, previous, key) == 0) {
                deleteUnused(key);
            }
        } catch (RuntimeException exception) {
            deleteUnused(key);
            throw exception;
        }
        return clip;
    }

    private void deleteUnused(String key) {
        try {
            storage.deleteWord(key);
        } catch (RuntimeException exception) {
            log.warn("미사용 단어 노래 음원 정리 실패. key={}", key, exception);
        }
    }
}

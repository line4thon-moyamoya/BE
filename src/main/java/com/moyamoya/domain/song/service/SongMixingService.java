package com.moyamoya.domain.song.service;

import com.moyamoya.domain.song.dto.response.SongMixResponse;
import com.moyamoya.domain.song.model.SongAudioPlacement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SongMixingService {
    private static final int MAX_VOICE_BYTES = 10 * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024;
    private final SongSpeechPlanner planner;
    private final SongWordAudioService wordAudioService;
    private final PythonSongMixerClient mixerClient;
    private final SongAudioStorageService storage;

    public SongMixingService(
            SongSpeechPlanner planner,
            SongWordAudioService wordAudioService,
            PythonSongMixerClient mixerClient,
            SongAudioStorageService storage
    ) {
        this.planner = planner;
        this.wordAudioService = wordAudioService;
        this.mixerClient = mixerClient;
        this.storage = storage;
    }

    public SongMixResponse mix(List<String> words) {
        var plan = planner.plan(words);
        byte[] backing = storage.downloadBacking();
        var indexes = new LinkedHashMap<String, Integer>();
        var voices = new ArrayList<byte[]>();
        var placements = new ArrayList<SongAudioPlacement>();
        var syllables = new LinkedHashMap<String, SongTtsService.Result>();
        long totalBytes = backing.length;
        for (var placement : plan) {
            String word = placement.text();
            Integer audioIndex = indexes.get(word);
            if (audioIndex == null) {
                byte[] voice = wordAudioService.getOrCreate(word, syllables);
                if (voice == null || voice.length == 0 || voice.length > MAX_VOICE_BYTES) {
                    throw new IllegalStateException("단어 TTS 음성 데이터가 올바르지 않습니다.");
                }
                totalBytes += voice.length;
                if (totalBytes > MAX_TOTAL_BYTES) {
                    throw new IllegalStateException("노래 합성 요청 크기가 50MB를 초과했습니다.");
                }
                audioIndex = voices.size();
                indexes.put(word, audioIndex);
                voices.add(voice);
            }
            placements.add(new SongAudioPlacement(audioIndex, placement.startSec(), null));
        }
        byte[] audio = mixerClient.mixWords(backing, voices, placements);
        return storage.upload(audio);
    }
}

package com.moyamoya.domain.song.service;

import com.moyamoya.domain.question.service.MoyaTtsService;
import com.moyamoya.domain.song.model.SongAudioPlacement;
import com.moyamoya.domain.song.model.SongMixResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SongMixingService {
    private static final long MAX_BACKING_BYTES = 20L * 1024 * 1024;
    private static final int MAX_VOICE_BYTES = 10 * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024;
    private final SongSpeechPlanner planner;
    private final MoyaTtsService ttsService;
    private final PythonSongMixerClient mixerClient;
    private final String backingTrackPath;
    private final Path outputDirectory;

    public SongMixingService(
            SongSpeechPlanner planner,
            MoyaTtsService ttsService,
            PythonSongMixerClient mixerClient,
            @Value("${song.mixing.backing-track-path:}") String backingTrackPath,
            @Value("${song.mixing.output-directory:local-song-output}") String outputDirectory
    ) {
        this.planner = planner;
        this.ttsService = ttsService;
        this.mixerClient = mixerClient;
        this.backingTrackPath = backingTrackPath;
        this.outputDirectory = Path.of(outputDirectory);
    }

    public SongMixResult mix(List<String> words) {
        var plan = planner.plan(words);
        if (backingTrackPath == null || backingTrackPath.isBlank()) {
            throw new IllegalStateException("SONG_BACKING_TRACK_PATH 설정이 필요합니다.");
        }
        try {
            Path backingPath = Path.of(backingTrackPath);
            if (!Files.isRegularFile(backingPath) || Files.size(backingPath) == 0
                    || Files.size(backingPath) > MAX_BACKING_BYTES) {
                throw new IllegalStateException("반주 파일은 20MB 이하의 비어 있지 않은 파일이어야 합니다.");
            }
            byte[] backing = Files.readAllBytes(backingPath);
            Files.createDirectories(outputDirectory);
            var indexes = new LinkedHashMap<String, Integer>();
            var voices = new ArrayList<byte[]>();
            var placements = new ArrayList<SongAudioPlacement>();
            long totalBytes = backing.length;
            for (int index = 0; index < plan.size(); index++) {
                var speech = plan.get(index);
                Integer audioIndex = indexes.get(speech.text());
                if (audioIndex == null) {
                    byte[] voice = ttsService.synthesize(speech.text());
                    if (voice == null || voice.length == 0 || voice.length > MAX_VOICE_BYTES) {
                        throw new IllegalStateException("단어 TTS 음성 데이터가 올바르지 않습니다.");
                    }
                    totalBytes += voice.length;
                    if (totalBytes > MAX_TOTAL_BYTES) {
                        throw new IllegalStateException("노래 합성 요청 크기가 50MB를 초과했습니다.");
                    }
                    audioIndex = voices.size();
                    indexes.put(speech.text(), audioIndex);
                    voices.add(voice);
                }
                double nextStart = index + 1 < plan.size() ? plan.get(index + 1).startSec() : 32.0;
                Double availableSec = speech.text().codePointCount(0, speech.text().length()) >= 6
                        ? null : Math.rint((nextStart - speech.startSec()) * 1000) / 1000;
                placements.add(new SongAudioPlacement(audioIndex, speech.startSec(), availableSec));
            }
            byte[] audio = mixerClient.mix(backing, voices, placements);
            Path outputPath = outputDirectory.resolve("song-" + UUID.randomUUID() + ".wav");
            Files.write(outputPath, audio);
            return new SongMixResult(outputPath, audio);
        } catch (IOException exception) {
            throw new IllegalStateException("노래 합성 파일을 읽거나 저장할 수 없습니다.", exception);
        }
    }
}

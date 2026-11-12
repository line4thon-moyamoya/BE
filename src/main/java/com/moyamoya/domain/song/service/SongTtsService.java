package com.moyamoya.domain.song.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongTtsService {
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_AUDIO_BYTES = 10 * 1024 * 1024;
    private static final double MIN_WINDOW_RMS = 32768 * Math.pow(10, -40.0 / 20);
    private final SongGoogleTtsService googleTtsService;
    private final PythonSongMixerClient processorClient;

    public byte[] synthesize(String text) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("노래 TTS 생성이 중단됐습니다.");
            }
            byte[] original = googleTtsService.synthesize(text);
            if (hasAudibleSpeech(original)) {
                byte[] processed = processorClient.processVoice(original);
                if (hasAudibleSpeech(processed)) {
                    return processed;
                }
            }
            log.warn("노래 TTS 음량 부족. text={}, attempt={}/{}", text, attempt, MAX_ATTEMPTS);
        }
        throw new IllegalStateException("노래 TTS 음량이 너무 작거나 무음입니다.");
    }

    static boolean hasAudibleSpeech(byte[] audio) {
        if (audio == null || audio.length == 0) {
            return false;
        }
        if (audio.length > MAX_AUDIO_BYTES) {
            throw new IllegalStateException("노래 TTS 음성이 10MB를 초과했습니다.");
        }
        try (var stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(audio))) {
            var format = stream.getFormat();
            if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                    || format.getSampleSizeInBits() != 16 || format.isBigEndian()
                    || format.getChannels() < 1 || format.getSampleRate() <= 0
                    || format.getFrameSize() != format.getChannels() * 2) {
                throw new IllegalStateException("노래 TTS는 PCM 16비트 WAV여야 합니다.");
            }
            int windowFrames = Math.max(1, Math.round(format.getSampleRate() * 0.02f));
            int windowBytes = windowFrames * format.getFrameSize();
            byte[] pcm = stream.readNBytes(MAX_AUDIO_BYTES + 1);
            if (pcm.length > MAX_AUDIO_BYTES) {
                throw new IllegalStateException("노래 TTS 음성이 10MB를 초과했습니다.");
            }
            double audibleSeconds = 0;
            for (int offset = 0; offset < pcm.length; offset += windowBytes) {
                int end = Math.min(offset + windowBytes, pcm.length);
                double squareSum = 0;
                int count = 0;
                for (int index = offset; index + 1 < end; index += 2) {
                    int sample = (short) ((pcm[index] & 0xff) | (pcm[index + 1] << 8));
                    squareSum += (double) sample * sample;
                    count++;
                }
                if (count > 0 && Math.sqrt(squareSum / count) >= MIN_WINDOW_RMS) {
                    audibleSeconds += (double) count / format.getChannels() / format.getSampleRate();
                }
            }
            return audibleSeconds >= 0.04;
        } catch (UnsupportedAudioFileException | IOException exception) {
            throw new IllegalStateException("노래 TTS WAV를 읽을 수 없습니다.", exception);
        }
    }
}

package com.moyamoya.domain.song.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SongTtsService {
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_AUDIO_BYTES = 10 * 1024 * 1024;
    private static final double MIN_WINDOW_RMS = 32768 * Math.pow(10, -40.0 / 20);
    private final SongGoogleTtsService googleTtsService;
    private final PythonSongMixerClient processorClient;

    record Result(byte[] audio, boolean qualityPassed) {}

    Result synthesize(String text) {
        byte[] best = null;
        double bestScore = -1;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("노래 TTS 생성이 중단됐습니다.");
            }
            byte[] original = googleTtsService.synthesize(text);
            var originalQuality = inspect(original);
            byte[] processed = processorClient.processVoice(original);
            var processedQuality = inspect(processed);
            if (originalQuality.audible() && processedQuality.audible()) {
                return new Result(processed, true);
            }
            if (processedQuality.score() > bestScore) {
                best = processed;
                bestScore = processedQuality.score();
            }
        }
        return new Result(best, false);
    }

    static boolean hasAudibleSpeech(byte[] audio) {
        return inspect(audio).audible();
    }

    private record Quality(boolean audible, double score) {}

    private static Quality inspect(byte[] audio) {
        if (audio == null || audio.length == 0) {
            throw new IllegalStateException("노래 TTS 음성 데이터가 비어 있습니다.");
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
            long audibleSamples = 0;
            double score = 0;
            for (int offset = 0; offset < pcm.length; offset += windowBytes) {
                int end = Math.min(offset + windowBytes, pcm.length);
                double squareSum = 0;
                int count = 0;
                for (int index = offset; index + 1 < end; index += 2) {
                    int sample = (short) ((pcm[index] & 0xff) | (pcm[index + 1] << 8));
                    squareSum += (double) sample * sample;
                    count++;
                }
                if (count > 0) {
                    double duration = (double) count / format.getChannels() / format.getSampleRate();
                    double rms = Math.sqrt(squareSum / count);
                    score += Math.min(rms, MIN_WINDOW_RMS) * duration;
                    if (rms >= MIN_WINDOW_RMS) {
                        audibleSamples += count;
                    }
                }
            }
            long requiredFrames = (long) Math.ceil(0.04 * format.getSampleRate());
            return new Quality(audibleSamples >= requiredFrames * format.getChannels()
                    && hasSustainedAudio(pcm, format, requiredFrames), score);
        } catch (UnsupportedAudioFileException | IOException exception) {
            throw new IllegalStateException("노래 TTS WAV를 읽을 수 없습니다.", exception);
        }
    }

    private static boolean hasSustainedAudio(byte[] pcm, AudioFormat format, long requiredFrames) {
        int windowFrames = Math.max(1, Math.round(format.getSampleRate() * 0.005f));
        long[] audibleFramesByOffset = new long[windowFrames];
        double[] energies = new double[windowFrames];
        double squareSum = 0;
        int frameCount = pcm.length / format.getFrameSize();
        double requiredEnergy = MIN_WINDOW_RMS * MIN_WINDOW_RMS * windowFrames * format.getChannels();
        for (int frame = 0; frame < frameCount; frame++) {
            int slot = frame % windowFrames;
            squareSum -= energies[slot];
            double energy = 0;
            int offset = frame * format.getFrameSize();
            for (int channel = 0; channel < format.getChannels(); channel++) {
                int index = offset + channel * 2;
                int sample = (short) ((pcm[index] & 0xff) | (pcm[index + 1] << 8));
                energy += (double) sample * sample;
            }
            energies[slot] = energy;
            squareSum += energy;
            if (frame + 1 >= windowFrames && squareSum >= requiredEnergy) {
                audibleFramesByOffset[slot] += windowFrames;
                if (audibleFramesByOffset[slot] >= requiredFrames) {
                    return true;
                }
            }
        }
        return false;
    }
}

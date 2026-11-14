package com.moyamoya.domain.song.service;

import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import org.springframework.stereotype.Service;

@Service
public class SongGoogleTtsService {
    public byte[] synthesize(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("노래 TTS 텍스트가 비어 있습니다.");
        }
        try (var client = TextToSpeechClient.create()) {
            var input = SynthesisInput.newBuilder().setText(text).build();
            var voice = VoiceSelectionParams.newBuilder()
                    .setLanguageCode("ko-KR").setName("ko-KR-Neural2-A").build();
            var config = AudioConfig.newBuilder().setAudioEncoding(AudioEncoding.LINEAR16).build();
            return client.synthesizeSpeech(input, voice, config).getAudioContent().toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("노래용 Google TTS 음성 생성 실패", exception);
        }
    }
}

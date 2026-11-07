package com.moyamoya.domain.question.service;

import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GoogleTtsService {

    @Value("${google.tts.language-code}")
    private String languageCode;

    @Value("${google.tts.voice-name}")
    private String voiceName;

    public byte[] synthesize(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("TTS 텍스트가 비어 있습니다.");
        }

        try (TextToSpeechClient client = TextToSpeechClient.create()) {

            SynthesisInput input = SynthesisInput.newBuilder()
                    .setText(text)
                    .build();

            VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                    .setLanguageCode(languageCode)
                    .setName(voiceName)
                    .build();

            AudioConfig audioConfig = AudioConfig.newBuilder()
                    .setAudioEncoding(AudioEncoding.LINEAR16)
                    .build();

            SynthesizeSpeechResponse response = client.synthesizeSpeech(
                    input,
                    voice,
                    audioConfig
            );

            ByteString audio = response.getAudioContent();
            return audio.toByteArray();

        } catch (Exception e) {
            throw new IllegalStateException("Google TTS 음성 생성 실패", e);
        }
    }
}


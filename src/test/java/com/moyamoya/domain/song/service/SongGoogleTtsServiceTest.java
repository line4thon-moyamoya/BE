package com.moyamoya.domain.song.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;

class SongGoogleTtsServiceTest {
    @Test
    void usesNeural2WithOriginalTextAndLinear16WithoutChangingSharedConfiguration() throws Exception {
        var client = mock(TextToSpeechClient.class);
        try (var factory = mockStatic(TextToSpeechClient.class)) {
            factory.when(TextToSpeechClient::create).thenReturn(client);
            var input = SynthesisInput.newBuilder().setText("예").build();
            var voice = VoiceSelectionParams.newBuilder()
                    .setLanguageCode("ko-KR").setName("ko-KR-Neural2-A").build();
            var config = AudioConfig.newBuilder().setAudioEncoding(AudioEncoding.LINEAR16).build();
            when(client.synthesizeSpeech(input, voice, config)).thenReturn(SynthesizeSpeechResponse.newBuilder()
                    .setAudioContent(ByteString.copyFrom(new byte[]{1, 2})).build());
            assertThat(new SongGoogleTtsService().synthesize("예")).containsExactly(1, 2);
            verify(client).synthesizeSpeech(input, voice, config);
            verify(client).close();
        }
    }

    @Test
    void invalidTextDoesNotCreatePaidClient() {
        try (var factory = mockStatic(TextToSpeechClient.class)) {
            assertThatIllegalArgumentException().isThrownBy(() -> new SongGoogleTtsService().synthesize(" "));
            factory.verifyNoInteractions();
        }
    }
}

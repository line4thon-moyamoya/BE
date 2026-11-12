package com.moyamoya.domain.song.service;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.song.model.SongAudioPlacement;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

class PythonSongMixerClientTest {
    @Test
    void processesVoiceThroughSongOnlyRoute() {
        byte[] wav = {82, 73, 70, 70, 1};
        var builder = RestClient.builder().requestInterceptor((request, body, execution) -> {
            assertThat(request.getURI().toString()).isEqualTo("http://processor.test/songs/process");
            assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
            assertThat(request.getHeaders().getContentType().isCompatibleWith(MediaType.MULTIPART_FORM_DATA)).isTrue();
            assertThat(new String(body, StandardCharsets.UTF_8))
                    .contains("name=\"audio\"", "filename=\"voice.wav\"", "audio/wav");
            var response = new MockClientHttpResponse(wav, HttpStatus.OK);
            response.getHeaders().setContentType(MediaType.parseMediaType("audio/wav"));
            return response;
        });
        var client = new PythonSongMixerClient(builder, new ObjectMapper(), "http://processor.test", 2000, 65000);
        assertThat(client.processVoice(wav)).isEqualTo(wav);
    }

    @Test
    void emptyProcessedVoiceIsRejected() {
        var builder = RestClient.builder().requestInterceptor((request, body, execution) ->
                new MockClientHttpResponse(new byte[0], HttpStatus.OK));
        var client = new PythonSongMixerClient(builder, new ObjectMapper(), "http://processor.test", 2000, 65000);
        assertThatIllegalStateException().isThrownBy(() -> client.processVoice(new byte[]{1}))
                .withMessage("노래 음성 후처리 결과가 비어 있습니다.");
    }

    @Test
    void voiceProcessingServerErrorPreservesCause() {
        var builder = RestClient.builder().requestInterceptor((request, body, execution) ->
                new MockClientHttpResponse(new byte[0], HttpStatus.SERVICE_UNAVAILABLE));
        var client = new PythonSongMixerClient(builder, new ObjectMapper(), "http://processor.test", 2000, 65000);
        assertThatIllegalStateException().isThrownBy(() -> client.processVoice(new byte[]{1}))
                .withMessage("노래 음성 후처리 서버 호출에 실패했습니다.")
                .withCauseInstanceOf(org.springframework.web.client.RestClientResponseException.class);
    }

    @Test
    void sendsBackingVoicesAndJsonTimingsAsMultipart() {
        byte[] wav = {82, 73, 70, 70, 1};
        var builder = RestClient.builder().requestInterceptor((request, body, execution) -> {
            assertThat(request.getURI().toString()).isEqualTo("http://processor.test/songs/mix");
            assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
            assertThat(request.getHeaders().getContentType().isCompatibleWith(MediaType.MULTIPART_FORM_DATA)).isTrue();
            String multipart = new String(body, StandardCharsets.UTF_8);
            assertThat(multipart).contains("name=\"backing\"", "filename=\"backing.audio\"",
                    "name=\"voices\"", "filename=\"voice-0.wav\"", "name=\"metadata\"",
                    "\"audioIndex\":0", "\"startSec\":12.99", "\"availableSec\":0.5");
            var response = new MockClientHttpResponse(wav, HttpStatus.OK);
            response.getHeaders().setContentType(MediaType.parseMediaType("audio/wav"));
            return response;
        });
        var client = new PythonSongMixerClient(builder, new ObjectMapper(), "http://processor.test", 2000, 65000);

        assertThat(client.mix(new byte[]{1}, List.of(new byte[]{2}),
                List.of(new SongAudioPlacement(0, 12.99, 0.5)))).isEqualTo(wav);
    }

    @Test
    void serverFailureIsTranslated() {
        var builder = RestClient.builder().requestInterceptor((request, body, execution) ->
                new MockClientHttpResponse(new byte[0], HttpStatus.UNPROCESSABLE_ENTITY));
        var client = new PythonSongMixerClient(builder, new ObjectMapper(), "http://processor.test", 2000, 65000);

        assertThatIllegalStateException().isThrownBy(() ->
                client.mix(new byte[]{1}, List.of(new byte[]{2}), List.of(new SongAudioPlacement(0, 12, null))))
                .withMessage("노래 합성 서버 호출에 실패했습니다.");
    }

    @Test
    void emptyResponseIsRejected() {
        var builder = RestClient.builder().requestInterceptor((request, body, execution) ->
                new MockClientHttpResponse(new byte[0], HttpStatus.OK));
        var client = new PythonSongMixerClient(builder, new ObjectMapper(), "http://processor.test", 2000, 65000);

        assertThatIllegalStateException().isThrownBy(() ->
                client.mix(new byte[]{1}, List.of(new byte[]{2}), List.of(new SongAudioPlacement(0, 12, null))))
                .withMessage("노래 합성 결과가 비어 있습니다.");
    }
}

package com.moyamoya.domain.song.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.song.model.SongAudioPlacement;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PythonSongMixerClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PythonSongMixerClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${tts.processor-url}") String processorUrl,
            @Value("${tts.processor-connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${song.mixing.processor-read-timeout-ms:65000}") int readTimeoutMs
    ) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restClient = builder.baseUrl(processorUrl).requestFactory(factory).build();
        this.objectMapper = objectMapper;
    }

    public byte[] mixWords(byte[] backing, List<byte[]> words, List<SongAudioPlacement> placements) {
        return requestMix("/songs/mix", backing, words, placements, true);
    }

    public byte[] composeWord(List<byte[]> voices, List<SongAudioPlacement> placements) {
        return requestMix("/songs/word", null, voices, placements, false);
    }

    private byte[] requestMix(String route, byte[] backing, List<byte[]> voices,
            List<SongAudioPlacement> placements, boolean preparedWords) {
        var body = new LinkedMultiValueMap<String, Object>();
        if (backing != null) {
            body.add("backing", audioPart("backing.audio", backing, MediaType.APPLICATION_OCTET_STREAM));
        }
        for (int index = 0; index < voices.size(); index++) {
            body.add("voices", audioPart("voice-" + index + ".wav", voices.get(index), MediaType.parseMediaType("audio/wav")));
        }
        try {
            body.add("metadata", objectMapper.writeValueAsString(Map.of("placements", placements,
                    "preparedWords", preparedWords)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("음성 배치 정보를 변환할 수 없습니다.", exception);
        }
        try {
            byte[] audio = restClient.post().uri(route)
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().body(byte[].class);
            if (audio == null || audio.length == 0) {
                throw new IllegalStateException("노래 합성 결과가 비어 있습니다.");
            }
            return audio;
        } catch (RestClientException exception) {
            throw new IllegalStateException("노래 합성 서버 호출에 실패했습니다.", exception);
        }
    }

    public byte[] processVoice(byte[] voice) {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("audio", audioPart("voice.wav", voice, MediaType.parseMediaType("audio/wav")));
        try {
            byte[] audio = restClient.post().uri("/songs/process")
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().body(byte[].class);
            if (audio == null || audio.length == 0) {
                throw new IllegalStateException("노래 음성 후처리 결과가 비어 있습니다.");
            }
            return audio;
        } catch (RestClientException exception) {
            throw new IllegalStateException("노래 음성 후처리 서버 호출에 실패했습니다.", exception);
        }
    }

    private HttpEntity<ByteArrayResource> audioPart(String filename, byte[] audio, MediaType contentType) {
        var resource = new ByteArrayResource(audio) {
            @Override
            public String getFilename() { return filename; }
        };
        var headers = new HttpHeaders();
        headers.setContentType(contentType);
        return new HttpEntity<>(resource, headers);
    }
}

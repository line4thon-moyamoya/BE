package com.moyamoya.domain.question.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PythonAudioProcessorClient {

    private final RestClient restClient;

    public PythonAudioProcessorClient(
            @Value("${tts.processor-url}") String processorUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(processorUrl)
                .build();
    }

    public byte[] process(byte[] wavBytes) {
        ByteArrayResource resource =
                new ByteArrayResource(wavBytes) {
                    @Override
                    public String getFilename() {
                        return "tts.wav";
                    }
                };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(
                MediaType.parseMediaType("audio/wav")
        );

        MultiValueMap<String, Object> body =
                new LinkedMultiValueMap<>();

        body.add("audio", new HttpEntity<>(resource, headers));

        byte[] processed;
        try {
            // Python Parselmouth 서버 호출
            processed = restClient.post()
                    .uri("/process")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "TTS 후처리 서버 호출에 실패했습니다.",
                    exception
            );
        }

        if (processed == null || processed.length == 0) {
            throw new IllegalStateException(
                    "TTS 후처리 결과가 비어 있습니다."
            );
        }

        return processed;
    }
}

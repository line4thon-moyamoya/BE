package com.moyamoya.domain.question.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.question.exception.QuestionErrorCode;
import com.moyamoya.global.exception.CustomException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

@Service
public class SttService {

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String invokeUrl;
    private final String secretKey;

    public SttService(
            ObjectMapper objectMapper,
            @Value("${clova.speech.invoke-url}") String invokeUrl,
            @Value("${clova.speech.secret-key}") String secretKey,
            @Value("${clova.speech.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${clova.speech.read-timeout-ms:10000}") int readTimeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.invokeUrl = invokeUrl;
        this.secretKey = secretKey;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public String transcribe(MultipartFile audio) {
        validateAudio(audio);

        try {
            byte[] audioBytes = audio.getBytes();
            String requestUrl = appendLangKor(invokeUrl);

            String response = restClient.post()
                    .uri(requestUrl)
                    .header("X-CLOVASPEECH-API-KEY", secretKey)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(audioBytes)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                throw new CustomException(
                        QuestionErrorCode.STT_RECOGNITION_FAILED
                );
            }

            JsonNode result = objectMapper.readTree(response);

            String recognizedText = result.path("text").asText("");

            if (recognizedText.isBlank()) {
                throw new CustomException(
                        QuestionErrorCode.STT_NO_SPEECH
                );
            }

            return recognizedText.trim();

        } catch (IOException | RestClientException e) {
            throw new CustomException(
                    QuestionErrorCode.STT_RECOGNITION_FAILED
            );
        }
    }

    private void validateAudio(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new CustomException(
                    QuestionErrorCode.INVALID_AUDIO_FILE
            );
        }

        if (audio.getSize() > 10 * 1024 * 1024) {
            throw new CustomException(
                    QuestionErrorCode.INVALID_AUDIO_FILE
            );
        }

        String filename = audio.getOriginalFilename();

        if (filename == null ||
                !filename.toLowerCase(Locale.ROOT).matches(
                        ".*\\.(wav|mp3|m4a|ogg|flac|aac|ac3)$"
                )) {
            throw new CustomException(
                    QuestionErrorCode.INVALID_AUDIO_FILE
            );
        }
    }

    private String appendLangKor(String baseUrl) {
        if (baseUrl.contains("lang=")) {
            return baseUrl;
        }
        return baseUrl.contains("?")
                ? baseUrl + "&lang=Kor"
                : baseUrl + "?lang=Kor";
    }
}

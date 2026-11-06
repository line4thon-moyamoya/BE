package com.moyamoya.domain.word.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.word.dto.response.RepetitionResponse;
import com.moyamoya.domain.word.exception.MultipartUploadExceptionHandler;
import com.moyamoya.domain.word.exception.WordExceptionHandler;
import com.moyamoya.domain.word.service.RepetitionService;
import com.moyamoya.global.exception.GlobalExceptionHandler;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@SpringBootTest(classes = RepetitionUploadTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=test", "spring.servlet.multipart.max-file-size=1KB",
                "spring.servlet.multipart.max-request-size=4KB", "server.servlet.context-path=/test"})
class RepetitionUploadTest {
    private static final String REPETITION = "/api/v1/children/2/words/1/repetitions";
    @Autowired private TestRestTemplate rest;
    @Autowired private RepetitionService service;

    @BeforeEach
    void resetService() {
        reset(service);
    }

    private ResponseEntity<String> upload(String path, int bytes) {
        ByteArrayResource resource = new ByteArrayResource(new byte[bytes]) {
            @Override
            public String getFilename() {
                return "voice.wav";
            }
        };
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("audio", resource);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    @Test
    void oversizedFileReturnsRepetition400BeforeControllerRuns() {
        var response = upload(REPETITION, 2048);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("\"code\":\"REPETITION400\"", "올바른 음성 파일을 전달해 주세요.");
        verifyNoInteractions(service);
    }

    @Test
    void oversizedRequestAlsoReturnsRepetition400() {
        var response = upload(REPETITION, 8192);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("\"code\":\"REPETITION400\"");
        verifyNoInteractions(service);
    }

    @Test
    void otherUploadRetainsExistingCommon500Response() {
        var response = upload("/other-upload", 2048);
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).contains("\"code\":\"COMMON500\"");
    }

    @Test
    void validFileStillReachesRepetitionController() {
        when(service.repeat(eq(7L), eq(2L), eq(1L), any()))
                .thenReturn(new RepetitionResponse(1L, 35L, true, true, null, null));
        var response = upload(REPETITION, 128);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"isCorrect\":true");
        verify(service).repeat(eq(7L), eq(2L), eq(1L), any());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
    @Import({RepetitionController.class, WordExceptionHandler.class, MultipartUploadExceptionHandler.class,
            GlobalExceptionHandler.class, OtherUploadController.class})
    static class TestApplication {
        @Bean
        RepetitionService repetitionService() {
            return mock(RepetitionService.class);
        }

        // 인증 테스트와 분리해 실제 서블릿의 업로드 제한만 검증
        @Bean
        Filter testPrincipal() {
            return (request, response, chain) -> chain.doFilter(new HttpServletRequestWrapper((HttpServletRequest) request) {
                @Override
                public Principal getUserPrincipal() {
                    return () -> "7";
                }
            }, response);
        }
    }

    @RestController
    static class OtherUploadController {
        @PostMapping(value = "/other-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        String upload(@RequestPart("audio") MultipartFile audio) {
            return "ok";
        }
    }
}

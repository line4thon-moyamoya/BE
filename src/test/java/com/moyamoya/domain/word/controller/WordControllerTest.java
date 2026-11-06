package com.moyamoya.domain.word.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.word.dto.response.WordDescriptionResponse;
import com.moyamoya.domain.word.exception.WordErrorCode;
import com.moyamoya.domain.word.exception.WordExceptionHandler;
import com.moyamoya.domain.word.service.WordService;
import com.moyamoya.global.config.SecurityConfig;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.exception.GlobalExceptionHandler;
import com.moyamoya.security.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringJUnitWebConfig(WordControllerTest.Config.class)
class WordControllerTest {
    @Configuration
    @EnableWebMvc
    @Import({WordController.class, WordExceptionHandler.class, GlobalExceptionHandler.class, SecurityConfig.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean WordService service() { return mock(WordService.class); }
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
        @Bean CorsConfigurationSource corsConfigurationSource() { return request -> null; }
        @Bean JwtTokenProvider tokens() {
            return new JwtTokenProvider(new JwtProperties("test-word-description-secret-at-least-32-bytes", 60_000L, 120_000L, 60_000L));
        }
    }
    @Autowired private WebApplicationContext context;
    @Autowired private WordService service;
    @Autowired private JwtTokenProvider tokens;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(service);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String auth() { return "Bearer " + tokens.issueTokens(7L).accessToken(); }

    @Test
    void returnsDescriptionAndUsesAuthenticatedUserWithoutParentToken() throws Exception {
        when(service.getWordDescription(7L, 1L, 2L))
                .thenReturn(new WordDescriptionResponse(1L, "적금", "아이 설명", "desc.mp3"));
        mvc.perform(get("/api/v1/words/1").param("childId", "2").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"success":true,"code":200,"message":"단어 정보를 조회했습니다.",
                         "data":{"wordId":1,"word":"적금","description":"아이 설명","descriptionAudioUrl":"desc.mp3"}}
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
        verify(service).getWordDescription(7L, 1L, 2L);
    }

    @Test
    void preservesNullFields() throws Exception {
        when(service.getWordDescription(7L, 1L, 2L)).thenReturn(new WordDescriptionResponse(1L, "적금", null, null));
        mvc.perform(get("/api/v1/words/1").param("childId", "2").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"success":true,"code":200,"message":"단어 정보를 조회했습니다.",
                         "data":{"wordId":1,"word":"적금","description":null,"descriptionAudioUrl":null}}
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test
    void requiresAccessToken() throws Exception {
        mvc.perform(get("/api/v1/words/1").param("childId", "2"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH401"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "refresh", "parent"})
    void rejectsWrongTokens(String type) throws Exception {
        String token = switch (type) {
            case "refresh" -> tokens.issueTokens(7L).refreshToken();
            case "parent" -> tokens.issueParentModeToken(7L);
            default -> "invalid";
        };
        mvc.perform(get("/api/v1/words/1").param("childId", "2").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"abc,2,WORD400", "999999999999999999999,2,WORD400", "1,abc,CHILD400", "1,'',CHILD400"})
    void rejectsInvalidParameterTypes(String word, String child, String code) throws Exception {
        mvc.perform(get("/api/v1/words/" + word).param("childId", child).header("Authorization", auth()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingChildParameter() throws Exception {
        mvc.perform(get("/api/v1/words/1").header("Authorization", auth()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CHILD400"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @EnumSource(value = WordErrorCode.class, names = {"INVALID_WORD_ID", "INVALID_CHILD_ID", "CHILD_ACCESS_DENIED", "CHILD_NOT_FOUND", "WORD404"})
    void returnsDomainErrors(WordErrorCode error) throws Exception {
        when(service.getWordDescription(7L, 1L, 2L)).thenThrow(new CustomException(error));
        mvc.perform(get("/api/v1/words/1").param("childId", "2").header("Authorization", auth()))
                .andExpect(status().is(error.getHttpStatus().value()))
                .andExpect(jsonPath("$.code").value(error.getCode()));
    }

    @Test
    void unexpectedFailureReturnsCommon500() throws Exception {
        when(service.getWordDescription(7L, 1L, 2L)).thenThrow(new IllegalStateException("test failure"));
        mvc.perform(get("/api/v1/words/1").param("childId", "2").header("Authorization", auth()))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("COMMON500"));
    }
}

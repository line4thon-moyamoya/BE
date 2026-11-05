package com.moyamoya.domain.wordbook.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.wordbook.dto.response.LearnedWordResponse;
import com.moyamoya.domain.wordbook.dto.response.WordbookResponse;
import com.moyamoya.domain.wordbook.exception.WordbookErrorCode;
import com.moyamoya.domain.wordbook.service.WordbookService;
import com.moyamoya.global.config.SecurityConfig;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.exception.GlobalExceptionHandler;
import com.moyamoya.security.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

@SpringJUnitWebConfig(WordbookControllerTest.Config.class)
class WordbookControllerTest {
    @Test
    void marksViewedWithoutRequestBodyAndReturnsNullData() throws Exception {
        mvc.perform(patch("/api/v1/children/2/learned-words/35/viewed")
                        .header("Authorization", accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"success": true, "code": 200, "message": "단어 카드 열람이 처리되었습니다.", "data": null}
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
        verify(service).markAsViewed(7L, 2L, 35L);
    }

    @Test
    void viewingWithoutAccessTokenReturns401() throws Exception {
        mvc.perform(patch("/api/v1/children/2/learned-words/35/viewed"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH401"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "refresh", "parent"})
    void viewingWithInvalidAccessTokenReturns401(String type) throws Exception {
        String token = switch (type) {
            case "refresh" -> tokens.issueTokens(7L).refreshToken();
            case "parent" -> tokens.issueParentModeToken(7L);
            default -> "invalid";
        };
        mvc.perform(patch("/api/v1/children/2/learned-words/35/viewed").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH401"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CHILD_ACCESS_DENIED", "CHILD_NOT_FOUND", "LEARNED_WORD_NOT_FOUND"})
    void viewingReturnsDomainError(String name) throws Exception {
        WordbookErrorCode error = WordbookErrorCode.valueOf(name);
        doThrow(new CustomException(error)).when(service).markAsViewed(7L, 2L, 35L);
        mvc.perform(patch("/api/v1/children/2/learned-words/35/viewed").header("Authorization", accessToken()))
                .andExpect(status().is(error.getHttpStatus().value()))
                .andExpect(jsonPath("$.code").value(error.getCode()))
                .andExpect(jsonPath("$.message").value(error.getMessage()));
    }

    @Test
    void viewingUnexpectedFailureReturnsCommon500() throws Exception {
        doThrow(new IllegalStateException("test failure")).when(service).markAsViewed(7L, 2L, 35L);
        mvc.perform(patch("/api/v1/children/2/learned-words/35/viewed").header("Authorization", accessToken()))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("COMMON500"));
    }
    @Configuration
    @EnableWebMvc
    @Import({WordbookController.class, SecurityConfig.class, JwtAuthenticationFilter.class,
            RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean WordbookService service() { return mock(WordbookService.class); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean CorsConfigurationSource corsConfigurationSource() { return request -> null; }
        @Bean JwtTokenProvider tokens() {
            return new JwtTokenProvider(new JwtProperties(
                    "test-only-wordbook-signing-key-at-least-32-bytes", 60_000L, 120_000L, 60_000L));
        }
    }

    @Autowired private WebApplicationContext context;
    @Autowired private WordbookService service;
    @Autowired private JwtTokenProvider tokens;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(service);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String accessToken() { return "Bearer " + tokens.issueTokens(7L).accessToken(); }

    @Test
    void returnsCardsWithExactJsonFieldNamesWithoutParentModeToken() throws Exception {
        when(service.getWordbook(7L, 2L)).thenReturn(new WordbookResponse(1,
                List.of(new LearnedWordResponse(35L, 1L, "적금", null, true, true))));
        mvc.perform(get("/api/v1/children/2/learned-words").header("Authorization", accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("단어 수첩을 조회했습니다."))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.learnedWords[0].learnedWordId").value(35))
                .andExpect(jsonPath("$.data.learnedWords[0].wordId").value(1))
                .andExpect(jsonPath("$.data.learnedWords[0].word").value("적금"))
                .andExpect(jsonPath("$.data.learnedWords[0].imageUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.learnedWords[0].stickerEarned").value(true))
                .andExpect(jsonPath("$.data.learnedWords[0].isNew").value(true))
                .andExpect(jsonPath("$.data.learnedWords[0].new").doesNotExist());
        verify(service).getWordbook(7L, 2L);
    }

    @Test
    void returnsEmptyList() throws Exception {
        when(service.getWordbook(7L, 2L)).thenReturn(new WordbookResponse(0, List.of()));
        mvc.perform(get("/api/v1/children/2/learned-words").header("Authorization", accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.learnedWords").isEmpty());
    }

    @Test
    void rejectsMissingAccessToken() throws Exception {
        mvc.perform(get("/api/v1/children/2/learned-words"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH401"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "refresh", "parent"})
    void rejectsInvalidOrWrongTypeAccessToken(String type) throws Exception {
        String token = switch (type) {
            case "refresh" -> tokens.issueTokens(7L).refreshToken();
            case "parent" -> tokens.issueParentModeToken(7L);
            default -> "invalid";
        };
        mvc.perform(get("/api/v1/children/2/learned-words").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH401"));
        verifyNoInteractions(service);
    }

    @Test
    void returnsForbiddenForOtherParentsChild() throws Exception {
        when(service.getWordbook(7L, 2L)).thenThrow(new CustomException(WordbookErrorCode.CHILD_ACCESS_DENIED));
        mvc.perform(get("/api/v1/children/2/learned-words").header("Authorization", accessToken()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CHILD403"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
    }

    @Test
    void returnsNotFoundForMissingChild() throws Exception {
        when(service.getWordbook(7L, 2L)).thenThrow(new CustomException(WordbookErrorCode.CHILD_NOT_FOUND));
        mvc.perform(get("/api/v1/children/2/learned-words").header("Authorization", accessToken()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CHILD404"))
                .andExpect(jsonPath("$.message").value("해당 아이를 찾을 수 없습니다."));
    }

    @Test
    void returnsCommonErrorForUnexpectedFailure() throws Exception {
        when(service.getWordbook(7L, 2L)).thenThrow(new IllegalStateException("test failure"));
        mvc.perform(get("/api/v1/children/2/learned-words").header("Authorization", accessToken()))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("COMMON500"));
    }
}

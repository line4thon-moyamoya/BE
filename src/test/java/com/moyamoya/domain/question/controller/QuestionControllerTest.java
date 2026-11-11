package com.moyamoya.domain.question.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyamoya.domain.question.dto.response.CandidateResponse;
import com.moyamoya.domain.question.dto.response.ConfirmWordResponse;
import com.moyamoya.domain.question.dto.response.QuestionCandidateResponse;
import com.moyamoya.domain.question.dto.response.SecondCandidateResponse;
import com.moyamoya.domain.question.dto.response.WordRequestSaveResponse;
import com.moyamoya.domain.question.exception.QuestionErrorCode;
import com.moyamoya.domain.question.service.QuestionConfirmService;
import com.moyamoya.domain.question.service.QuestionService;
import com.moyamoya.domain.question.service.WordRequestSaveService;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.exception.GlobalExceptionHandler;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class QuestionControllerTest {

    @Mock
    private QuestionService questionService;
    @Mock
    private QuestionConfirmService questionConfirmService;
    @Mock
    private WordRequestSaveService wordRequestSaveService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new QuestionController(
                        questionService,
                        questionConfirmService,
                        wordRequestSaveService
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void analyzeReturnsFoundCandidatesResponse() throws Exception {
        UUID sessionId = UUID.fromString("424f5cc7-5540-4036-b6dc-a2bde9218851");
        QuestionCandidateResponse response = new QuestionCandidateResponse(
                sessionId,
                "CANDIDATES_FOUND",
                "PARTICLE_REMOVAL",
                "적금이 뭐야",
                "적금",
                1,
                List.of(new CandidateResponse(
                        "c_001",
                        157L,
                        "적금",
                        "은행에 매달 정해진 돈을 넣어서 크게 모으는 거야.",
                        null,
                        "https://example.com/word-audio",
                        null,
                        null
                )),
                new QuestionCandidateResponse.Dialogue("적금 말하는 거야?", null, "base64-audio", "audio/wav"),
                "CANDIDATE_SELECTION"
        );
        when(questionService.analyzeAudio(eq(7L), eq(2L), any())).thenReturn(response);

        MockMultipartFile audio = new MockMultipartFile(
                "audio",
                "voice.wav",
                "audio/wav",
                "fake-wav".getBytes()
        );
        mockMvc.perform(multipart("/api/v1/questions/analyze")
                        .file(audio)
                        .param("childId", "2")
                        .principal(() -> "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("1차 단어 후보를 조회했습니다."))
                .andExpect(jsonPath("$.data.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.data.candidateCount").value(1))
                .andExpect(jsonPath("$.data.candidates[0].candidateId").value("c_001"))
                .andExpect(jsonPath("$.data.dialogue.audioContentType").value("audio/wav"));

        verify(questionService).analyzeAudio(eq(7L), eq(2L), any());
    }

    @Test
    void clarifyReturnsExpectedResponse() throws Exception {
        when(questionService.findSecondCandidates(7L, "session-1", com.moyamoya.domain.word.entity.HeardFrom.PARENTS))
                .thenReturn(new SecondCandidateResponse(
                        "session-1",
                        "CANDIDATES_FOUND",
                        1,
                        List.of(new CandidateResponse(
                                "c_002",
                                158L,
                                "예금",
                                "돈을 은행에 맡겨 두었다가 나중에 찾는 거야.",
                                null,
                                null,
                                "base64-word",
                                "audio/wav"
                        )),
                        new SecondCandidateResponse.Dialogue(
                                "예금 말하는 거야?",
                                null,
                                "base64-dialogue",
                                "audio/wav"
                        ),
                        "FINAL_CANDIDATE_SELECTION"
                ));

        mockMvc.perform(post("/api/v1/questions/session-1/clarify")
                        .principal(() -> "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "context": "PARENTS"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("2차 단어 후보를 조회했습니다."))
                .andExpect(jsonPath("$.data.sessionId").value("session-1"))
                .andExpect(jsonPath("$.data.candidateCount").value(1))
                .andExpect(jsonPath("$.data.dialogue.audioContentType").value("audio/wav"));
    }

    @Test
    void confirmReturnsExpectedResponse() throws Exception {
        when(questionConfirmService.confirmWord(7L, "session-1", "c_001"))
                .thenReturn(new ConfirmWordResponse(
                        "CONFIRMED",
                        157L,
                        "적금",
                        "은행에 매달 정해진 돈을 넣어서 크게 모으는 거야.",
                        null,
                        "https://example.com/word-audio",
                        "WORD_LEARNING"
                ));

        mockMvc.perform(post("/api/v1/questions/session-1/confirm")
                        .principal(() -> "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "candidateId": "c_001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("단어를 확정했습니다."))
                .andExpect(jsonPath("$.data.wordId").value(157))
                .andExpect(jsonPath("$.data.word").value("적금"));

        verify(questionConfirmService).confirmWord(7L, "session-1", "c_001");
    }

    @Test
    void saveWordRequestReturnsExpectedResponse() throws Exception {
        when(wordRequestSaveService.save(7L, "session-1"))
                .thenReturn(new WordRequestSaveResponse(
                        31L,
                        "PENDING",
                        "WORD_REQUEST_SAVED"
                ));

        mockMvc.perform(post("/api/v1/questions/session-1/word-request")
                        .principal(() -> "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("물어볼 단어로 저장되었습니다."))
                .andExpect(jsonPath("$.data.wordRequestId").value(31))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        verify(wordRequestSaveService).save(7L, "session-1");
    }

    @Test
    void clarifyReturnsQuestion400WhenContextIsInvalid() throws Exception {
        when(questionService.findSecondCandidates(7L, "session-1", com.moyamoya.domain.word.entity.HeardFrom.PARENTS))
                .thenThrow(new CustomException(QuestionErrorCode.INVALID_CONTEXT));

        mockMvc.perform(post("/api/v1/questions/session-1/clarify")
                        .principal(() -> "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "context": "PARENTS"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("QUESTION400_INVALID_CONTEXT"))
                .andExpect(jsonPath("$.message").value("어디서 들었는지 올바르게 선택해 주세요."));
    }

    @Test
    void confirmReturnsQuestion400WhenCandidateIsInvalid() throws Exception {
        when(questionConfirmService.confirmWord(7L, "session-1", "c_999"))
                .thenThrow(new CustomException(QuestionErrorCode.INVALID_CANDIDATE));

        mockMvc.perform(post("/api/v1/questions/session-1/confirm")
                        .principal(() -> "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "candidateId": "c_999"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("QUESTION400_INVALID_CANDIDATE"))
                .andExpect(jsonPath("$.message").value("선택할 수 없는 단어 후보입니다."));
    }

    @Test
    void confirmReturnsQuestion404WhenSessionIsNotFound() throws Exception {
        when(questionConfirmService.confirmWord(7L, "missing-session", "c_001"))
                .thenThrow(new CustomException(QuestionErrorCode.SESSION_NOT_FOUND));

        mockMvc.perform(post("/api/v1/questions/missing-session/confirm")
                        .principal(() -> "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "candidateId": "c_001"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("QUESTION404"))
                .andExpect(jsonPath("$.message").value("존재하지 않거나 만료된 질문입니다."));
    }

    @Test
    void confirmReturnsQuestion403WhenForbidden() throws Exception {
        when(questionConfirmService.confirmWord(7L, "session-1", "c_001"))
                .thenThrow(new CustomException(QuestionErrorCode.ACCESS_FORBIDDEN));

        mockMvc.perform(post("/api/v1/questions/session-1/confirm")
                        .principal(() -> "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "candidateId": "c_001"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("QUESTION403"))
                .andExpect(jsonPath("$.message").value("해당 질문 정보에 접근할 권한이 없습니다."));
    }

    @Test
    void malformedConfirmBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/questions/session-1/confirm")
                        .principal(() -> "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL400"));

        verifyNoInteractions(questionConfirmService);
    }
}

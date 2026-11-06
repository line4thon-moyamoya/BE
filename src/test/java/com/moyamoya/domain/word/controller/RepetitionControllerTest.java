package com.moyamoya.domain.word.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.moyamoya.domain.word.dto.response.RepetitionResponse;
import com.moyamoya.domain.word.exception.RepetitionErrorCode;
import com.moyamoya.domain.word.exception.WordExceptionHandler;
import com.moyamoya.domain.word.service.RepetitionService;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RepetitionControllerTest {
    private final RepetitionService service = mock(RepetitionService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new RepetitionController(service))
            .setControllerAdvice(new WordExceptionHandler(), new GlobalExceptionHandler()).build();
    private final MockMultipartFile audio = new MockMultipartFile("audio", "voice.wav", "audio/wav", new byte[]{1});
    private static final String PATH = "/api/v1/children/{childId}/words/{wordId}/repetitions";

    @Test
    void multipartUsesPrincipalAndReturnsExactBooleanFieldNames() throws Exception {
        when(service.repeat(eq(7L), eq(2L), eq(1L), any())).thenReturn(new RepetitionResponse(1L, 35L, true, true));
        mvc.perform(multipart(PATH, 2, 1).file(audio).principal(() -> "7"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"success":true,"code":200,"message":"따라 말하기 결과를 확인했습니다.",
                         "data":{"wordId":1,"learnedWordId":35,"isCorrect":true,"isNewAcquisition":true}}
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
        verify(service).repeat(7L, 2L, 1L, audio);
    }

    @Test
    void missingAudioReturnsRepetition400() throws Exception {
        mvc.perform(multipart(PATH, 2, 1).principal(() -> "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPETITION400"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"invalid,1,CHILD400", "2,invalid,WORD400"})
    void malformedIdsReturnDomainErrors(String childId, String wordId, String code) throws Exception {
        mvc.perform(multipart(PATH, childId, wordId).file(audio).principal(() -> "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"INVALID_AUDIO,400,REPETITION400", "STT_FAILED,502,REPETITION_STT_FAILED"})
    void returnsServiceError(RepetitionErrorCode error, int status, String code) throws Exception {
        when(service.repeat(anyLong(), anyLong(), anyLong(), any())).thenThrow(new CustomException(error));
        mvc.perform(multipart(PATH, 2, 1).file(audio).principal(() -> "7"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code));
    }
}

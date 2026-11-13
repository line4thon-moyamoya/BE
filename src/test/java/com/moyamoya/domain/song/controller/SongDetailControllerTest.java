package com.moyamoya.domain.song.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.moyamoya.domain.song.dto.response.SongDetailResponse;
import com.moyamoya.domain.song.dto.response.SongWordResponse;
import com.moyamoya.domain.song.entity.SongStatus;
import com.moyamoya.domain.song.exception.SongErrorCode;
import com.moyamoya.domain.song.exception.SongExceptionHandler;
import com.moyamoya.domain.song.service.SongService;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SongDetailControllerTest {
    private final SongService service = mock(SongService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SongController(service))
            .setControllerAdvice(new SongExceptionHandler(), new GlobalExceptionHandler()).build();
    private static final String PATH = "/api/v1/children/{childId}/songs/{songId}";

    @Test
    void readyReturnsDetailUsingDescriptionFieldAndPrincipal() throws Exception {
        when(service.getSongDetail(7L, 2L, 12L)).thenReturn(new SongDetailResponse(
                12L, "우리 노래 1", SongStatus.READY, "https://audio.test/12.wav",
                List.of(new SongWordResponse(1L, "적금", null, "돈을 조금씩 모으는 거야"))));

        mvc.perform(get(PATH, 2, 12).principal(() -> "7"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"success":true,"code":200,"message":"노래 상세 정보를 조회했습니다.",
                         "data":{"songId":12,"title":"우리 노래 1","status":"READY",
                         "audioUrl":"https://audio.test/12.wav",
                         "words":[{"wordId":1,"word":"적금","imageUrl":null,"description":"돈을 조금씩 모으는 거야"}]}}
                        """, JsonCompareMode.STRICT));
        verify(service).getSongDetail(7L, 2L, 12L);
    }

    @ParameterizedTest
    @CsvSource({"GENERATING,노래를 생성하고 있습니다.", "FAILED,노래 생성에 실패했습니다."})
    void unfinishedStatesReturn200AndEmptyDetail(SongStatus state, String message) throws Exception {
        when(service.getSongDetail(7L, 2L, 12L))
                .thenReturn(new SongDetailResponse(12L, "우리 노래 1", state, null, List.of()));

        mvc.perform(get(PATH, 2, 12).principal(() -> "7"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"success":true,"code":200,"message":"%s",
                         "data":{"songId":12,"title":"우리 노래 1","status":"%s","audioUrl":null,"words":[]}}
                        """.formatted(message, state), JsonCompareMode.STRICT));
    }

    @ParameterizedTest
    @CsvSource({"abc,12,CHILD400", "9223372036854775808,12,CHILD400",
            "2,abc,SONG400", "2,9223372036854775808,SONG400"})
    void malformedIdsReturnMatchingErrorBeforeService(String childId, String songId, String code) throws Exception {
        mvc.perform(get(PATH, childId, songId).principal(() -> "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"INVALID_CHILD_ID,400,CHILD400", "INVALID_SONG_ID,400,SONG400",
            "CHILD_ACCESS_DENIED,403,CHILD403", "CHILD_NOT_FOUND,404,CHILD404", "SONG_NOT_FOUND,404,SONG404"})
    void returnsDomainErrors(SongErrorCode error, int status, String code) throws Exception {
        when(service.getSongDetail(7L, 2L, 12L)).thenThrow(new CustomException(error));

        mvc.perform(get(PATH, 2, 12).principal(() -> "7"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(error.getMessage()));
    }

    @Test
    void signingFailureReturnsCommon500() throws Exception {
        when(service.getSongDetail(7L, 2L, 12L)).thenThrow(new IllegalStateException("sign failed"));

        mvc.perform(get(PATH, 2, 12).principal(() -> "7"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON500"));
    }
}

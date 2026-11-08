package com.moyamoya.domain.song.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.moyamoya.domain.song.dto.response.SongListResponse;
import com.moyamoya.domain.song.dto.response.SongSummaryResponse;
import com.moyamoya.domain.song.exception.SongErrorCode;
import com.moyamoya.domain.song.exception.SongExceptionHandler;
import com.moyamoya.domain.song.service.SongService;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SongControllerTest {
    private final SongService service = mock(SongService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SongController(service))
            .setControllerAdvice(new SongExceptionHandler(), new GlobalExceptionHandler()).build();
    private static final String PATH = "/api/v1/children/{childId}/songs";

    @Test
    void returnsSpecifiedResponseAndPassesPrincipal() throws Exception {
        when(service.getSongs(7L, 2L)).thenReturn(new SongListResponse(List.of(
                new SongSummaryResponse(12L, "우리 노래 1", List.of("영수증", "소방차", "용기", "적금", "통장")))));

        mvc.perform(get(PATH, 2).principal(() -> "7"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"success":true,"code":200,"message":"노래 목록을 조회했습니다.",
                         "data":{"songs":[{"songId":12,"title":"우리 노래 1",
                         "words":["영수증","소방차","용기","적금","통장"]}]}}
                        """, JsonCompareMode.STRICT));
        verify(service).getSongs(7L, 2L);
    }

    @Test
    void noSongsReturnsEmptyArray() throws Exception {
        when(service.getSongs(7L, 2L)).thenReturn(new SongListResponse(List.of()));

        mvc.perform(get(PATH, 2).principal(() -> "7"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"success":true,"code":200,"message":"노래 목록을 조회했습니다.","data":{"songs":[]}}
                        """, JsonCompareMode.STRICT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "9223372036854775808"})
    void malformedChildIdReturns400BeforeService(String id) throws Exception {
        mvc.perform(get(PATH, id).principal(() -> "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHILD400"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"INVALID_CHILD_ID,400,CHILD400", "CHILD_ACCESS_DENIED,403,CHILD403", "CHILD_NOT_FOUND,404,CHILD404"})
    void returnsDomainErrors(SongErrorCode error, int status, String code) throws Exception {
        when(service.getSongs(7L, 2L)).thenThrow(new CustomException(error));

        mvc.perform(get(PATH, 2).principal(() -> "7"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(error.getMessage()));
    }
}

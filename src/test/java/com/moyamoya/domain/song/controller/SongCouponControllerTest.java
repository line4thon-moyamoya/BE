package com.moyamoya.domain.song.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.moyamoya.domain.song.dto.response.SongCouponResponse;
import com.moyamoya.domain.song.exception.SongErrorCode;
import com.moyamoya.domain.song.exception.SongExceptionHandler;
import com.moyamoya.domain.song.service.SongCouponService;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SongCouponControllerTest {
    private final SongCouponService service = mock(SongCouponService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SongCouponController(service))
            .setControllerAdvice(new SongExceptionHandler(), new GlobalExceptionHandler()).build();
    private static final String PATH = "/api/v1/children/{childId}/song-coupons";

    @ParameterizedTest
    @ValueSource(longs = {0, 3})
    void returnsCommonResponseAndPassesPrincipal(long count) throws Exception {
        when(service.getCoupons(7L, 2L)).thenReturn(new SongCouponResponse(count));
        mvc.perform(get(PATH, 2).principal(() -> "7"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"success":true,"code":200,"message":"노래 쿠폰 현황을 조회했습니다.","data":{"couponCount":%d}}
                        """.formatted(count), JsonCompareMode.STRICT));
        verify(service).getCoupons(7L, 2L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "9223372036854775808"})
    void malformedChildIdReturns400(String id) throws Exception {
        mvc.perform(get(PATH, id).principal(() -> "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHILD400"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"INVALID_CHILD_ID,400,CHILD400", "CHILD_ACCESS_DENIED,403,CHILD403", "CHILD_NOT_FOUND,404,CHILD404"})
    void returnsDomainErrors(SongErrorCode error, int status, String code) throws Exception {
        when(service.getCoupons(7L, 2L)).thenThrow(new CustomException(error));
        mvc.perform(get(PATH, 2).principal(() -> "7"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(error.getMessage()));
    }
}

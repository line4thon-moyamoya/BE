package com.moyamoya.domain.song.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record SongCouponResponse(
        @Schema(description = "현재 노래에 사용하지 않은 쿠폰 수", example = "3")
        long couponCount
) {}

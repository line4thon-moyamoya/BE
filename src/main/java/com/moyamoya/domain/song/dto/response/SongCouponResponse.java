package com.moyamoya.domain.song.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record SongCouponResponse(
        @Schema(description = "현재 사용 가능한 노래 쿠폰 수 (0~4)", example = "3")
        long couponCount
) {}

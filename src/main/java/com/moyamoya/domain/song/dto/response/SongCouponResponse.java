package com.moyamoya.domain.song.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record SongCouponResponse(
        @Schema(description = "노래에 사용하지 않은 카드 수. 5장 사용 처리 연동 전에는 5 이상일 수 있습니다.", example = "3")
        long couponCount
) {}

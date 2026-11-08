package com.moyamoya.domain.song.controller;

import com.moyamoya.domain.song.dto.response.SongCouponResponse;
import com.moyamoya.domain.song.service.SongCouponService;
import com.moyamoya.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/children/{childId}/song-coupons")
@Tag(name = "Song", description = "노래 API")
public class SongCouponController {
    private final SongCouponService songCouponService;

    @Operation(summary = "노래 쿠폰 현황 조회",
            description = "로그인한 보호자의 아이가 보유한 미사용 카드 수를 조회합니다. "
                    + "노래에 연결된 카드는 생성 상태에 관계없이 제외합니다. "
                    + "5장 사용 처리 연동 전에는 5 이상을 반환할 수 있으며, 조회로 카드를 사용하지 않습니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "쿠폰 조회 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":true,"code":200,"message":"노래 쿠폰 현황을 조회했습니다.","data":{"couponCount":3}}
                            """))),
            @ApiResponse(responseCode = "400", description = "CHILD400: 올바르지 않은 아이 ID"),
            @ApiResponse(responseCode = "401", description = "AUTH401: 로그인 필요"),
            @ApiResponse(responseCode = "403", description = "CHILD403: 접근 권한 없음"),
            @ApiResponse(responseCode = "404", description = "CHILD404: 아이 없음"),
            @ApiResponse(responseCode = "500", description = "COMMON500: 서버 오류")
    })
    @GetMapping
    public ResponseEntity<BaseResponse<SongCouponResponse>> getCoupons(
            Principal principal,
            @Parameter(description = "쿠폰을 조회할 아이 ID", example = "1", required = true)
            @PathVariable("childId") Long childId
    ) {
        SongCouponResponse response = songCouponService.getCoupons(Long.valueOf(principal.getName()), childId);
        return ResponseEntity.ok(BaseResponse.success("노래 쿠폰 현황을 조회했습니다.", response));
    }
}

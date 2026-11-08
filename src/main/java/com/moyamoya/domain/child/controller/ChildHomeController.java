package com.moyamoya.domain.child.controller;

import com.moyamoya.domain.child.dto.response.ChildHomeResponse;
import com.moyamoya.domain.child.service.ChildHomeService;
import com.moyamoya.global.common.BaseResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/children")
@Tag(name = "Child Home", description = "아이 홈 조회 API")
public class ChildHomeController {

    private final ChildHomeService childHomeService;

    private static final String HOME_SUCCESS_EXAMPLE = """
            {
              "success": true,
              "code": 200,
              "message": "아이 홈 정보를 조회하였습니다.",
              "data": {
                "childId": 1,
                "nickname": "지우",
                "ageGroup": "AGE_5",
                "moyaType": "DEFAULT",
                "songCouponCount": 3
              }
            }
            """;

    private static final String UNAUTHORIZED_EXAMPLE = """
            {
              "success": false,
              "code": "AUTH401",
              "message": "로그인이 필요한 요청입니다.",
              "data": null
            }
            """;

    private static final String FORBIDDEN_EXAMPLE = """
            {
              "success": false,
              "code": "CHILD403_HOME",
              "message": "해당 아이의 홈 정보를 조회할 권한이 없습니다.",
              "data": null
            }
            """;

    private static final String NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "code": "CHILD404",
              "message": "아이 프로필을 찾을 수 없습니다.",
              "data": null
            }
            """;

    @Operation(
            summary = "아이 홈 조회",
            description = """
                    선택한 아이의 기본 프로필 정보와 사용 가능한 노래 쿠폰 개수를 조회합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "아이 홈 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = HOME_SUCCESS_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 childId 형식"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Access Token 누락 또는 유효하지 않음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "다른 보호자의 아이 조회 요청",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = FORBIDDEN_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "아이 프로필 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)
                    )
            )
    })
    @GetMapping("/{childId}/home")
    public ResponseEntity<BaseResponse<ChildHomeResponse>> getChildHome(
            Principal principal,
            @PathVariable Long childId
    ) {
        Long userId = Long.valueOf(principal.getName());

        ChildHomeResponse response =
                childHomeService.getChildHome(userId, childId);

        return ResponseEntity.ok(
                BaseResponse.success(
                        "아이 홈 정보를 조회하였습니다.",
                        response
                )
        );
    }
}

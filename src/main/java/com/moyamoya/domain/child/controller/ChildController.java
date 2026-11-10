package com.moyamoya.domain.child.controller;

import lombok.RequiredArgsConstructor;
import com.moyamoya.domain.child.dto.request.CreateChildRequest;
import com.moyamoya.domain.child.dto.request.InitialChildProfileRequest;
import com.moyamoya.domain.child.dto.request.UpdateChildRequest;
import com.moyamoya.domain.child.dto.response.ChildResponse;
import com.moyamoya.domain.child.dto.response.InitialChildProfileResponse;
import com.moyamoya.domain.child.service.ChildService;
import com.moyamoya.global.common.BaseResponse;
import com.moyamoya.security.parentmode.ParentModeRequired;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/children")
@Tag(name = "Child", description = "아이 프로필 API")
public class ChildController {
    @Operation(summary = "초기 아이 프로필 설정",
            description = "첫 실행 시 모야 유형을 선택합니다. 로그인 토큰만 필요하며 본인 계정의 아이만 설정할 수 있습니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "초기 아이 프로필 설정 성공"),
            @ApiResponse(responseCode = "400", description = "모야 유형 누락 또는 잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "로그인 필요"),
            @ApiResponse(responseCode = "403", description = "다른 계정의 아이 설정 요청"),
            @ApiResponse(responseCode = "404", description = "아이 프로필 없음")
    })
    @PatchMapping("/{childId}/initial-profile")
    public ResponseEntity<BaseResponse<InitialChildProfileResponse>> setupInitialProfile(
            Principal principal,
            @PathVariable("childId") Long childId,
            @RequestBody InitialChildProfileRequest request
    ) {
        InitialChildProfileResponse response = childService.setupInitialProfile(
                Long.valueOf(principal.getName()), childId, request);
        return ResponseEntity.ok(BaseResponse.success("초기 아이 프로필이 설정되었습니다.", response));
    }

    private static final String UPDATE_SUCCESS_EXAMPLE = """
            {"success": true, "code": 200, "message": "아이 프로필이 수정되었습니다.", "data": {}}
            """;
    private static final String INVALID_UPDATE_EXAMPLE = """
            {"success": false, "code": "CHILD400", "message": "수정할 아이 프로필 정보를 올바르게 입력해 주세요.", "data": null}
            """;
    private static final String EMPTY_UPDATE_EXAMPLE = """
            {"success": false, "code": "CHILD400", "message": "수정할 정보를 입력해 주세요.", "data": null}
            """;
    private static final String UPDATE_FORBIDDEN_EXAMPLE = """
            {"success": false, "code": "CHILD403", "message": "해당 아이 프로필을 수정할 권한이 없습니다.", "data": null}
            """;
    private static final String CHILD_NOT_FOUND_EXAMPLE = """
            {"success": false, "code": "CHILD404", "message": "아이 프로필을 찾을 수 없습니다.", "data": null}
            """;
    private static final String CREATE_REQUEST_EXAMPLE = """
            {
              "nickname": "서아",
              "ageGroup": "AGE_5",
              "moyaType": "CAR"
            }
            """;
    private static final String DEFAULT_MOYA_REQUEST_EXAMPLE = """
            {
              "nickname": "서아",
              "ageGroup": "AGE_5"
            }
            """;
    private static final String CREATE_SUCCESS_EXAMPLE = """
            {
              "success": true,
              "code": 201,
              "message": "아이 프로필이 추가되었습니다.",
              "data": {
                "childId": 2,
                "nickname": "서아",
                "ageGroup": "AGE_5",
                "moyaType": "CAR"
              }
            }
            """;
    private static final String INVALID_REQUEST_EXAMPLE = """
            {
              "success": false,
              "code": "CHILD400",
              "message": "아이 프로필 정보를 올바르게 입력해 주세요.",
              "data": null
            }
            """;
    private static final String INVALID_NICKNAME_EXAMPLE = """
            {
              "success": false,
              "code": "CHILD400_INVALID_NICKNAME",
              "message": "아이 별명을 올바르게 입력해 주세요.",
              "data": null
            }
            """;
    private static final String INVALID_AGE_EXAMPLE = """
            {
              "success": false,
              "code": "CHILD400_INVALID_AGE",
              "message": "올바른 나이 구간을 선택해 주세요.",
              "data": null
            }
            """;
    private static final String INVALID_MOYA_EXAMPLE = """
            {
              "success": false,
              "code": "CHILD400_INVALID_MOYA",
              "message": "올바른 모야 유형을 선택해 주세요.",
              "data": null
            }
            """;
    private static final String AUTHENTICATION_REQUIRED_EXAMPLE = """
            {
              "success": false,
              "code": "AUTH401",
              "message": "로그인이 필요한 요청입니다.",
              "data": null
            }
            """;
    private static final String INTERNAL_SERVER_ERROR_EXAMPLE = """
            {
              "success": false,
              "code": "COMMON500",
              "message": "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
              "data": null
            }
            """;

    private final ChildService childService;

    @Operation(summary = "아이 프로필 수정",
            description = "로그인한 보호자 소유의 아이 프로필을 수정합니다. 생략한 필드는 유지합니다. "
                    + "null은 허용하지 않으며 최소 한 필드를 전달해야 합니다. 보호자 모드 인증이 필요합니다.",
            parameters = @Parameter(
                    name = "X-Parent-Mode-Token",
                    description = "보호자 PIN 인증 후 발급받은 Parent Mode Token",
                    required = true,
                    in = ParameterIn.HEADER
            ),
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "아이 프로필 수정 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = UPDATE_SUCCESS_EXAMPLE))),
            @ApiResponse(responseCode = "400", description = "빈 요청, 잘못된 JSON 또는 입력값 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = {
                                    @ExampleObject(name = "잘못된 요청", value = INVALID_UPDATE_EXAMPLE),
                                    @ExampleObject(name = "빈 요청", value = EMPTY_UPDATE_EXAMPLE),
                                    @ExampleObject(name = "잘못된 별명", value = INVALID_NICKNAME_EXAMPLE),
                                    @ExampleObject(name = "잘못된 나이 구간", value = INVALID_AGE_EXAMPLE),
                                    @ExampleObject(name = "잘못된 모야 유형", value = INVALID_MOYA_EXAMPLE)
                            })),
            @ApiResponse(responseCode = "401", description = "로그인 필요",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = AUTHENTICATION_REQUIRED_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "다른 보호자의 아이 수정 요청",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = UPDATE_FORBIDDEN_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "아이 프로필 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = CHILD_NOT_FOUND_EXAMPLE))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_EXAMPLE)))
    })
    @ParentModeRequired
    @PatchMapping("/{childId}")
    public ResponseEntity<BaseResponse<Map<String, Object>>> updateChild(
            Principal principal,
            @PathVariable("childId") Long childId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UpdateChildRequest.class),
                            examples = {
                                    @ExampleObject(name = "프로필 수정", value = """
                                            {"nickname": "지우", "ageGroup": "AGE_3_4", "moyaType": "DEFAULT"}
                                            """),
                                    @ExampleObject(name = "별명만 수정", value = "{\"nickname\": \"서아\"}")
                            }))
            @RequestBody UpdateChildRequest request
    ) {
        childService.updateChild(Long.valueOf(principal.getName()), childId, request);
        return ResponseEntity.ok(BaseResponse.success("아이 프로필이 수정되었습니다.", Map.of()));
    }

    @Operation(summary = "아이 프로필 추가",
            description = "로그인한 보호자에게 아이를 추가합니다. 별명과 나이 구간은 필수이며, "
                    + "모야 유형을 생략하거나 null로 보내면 DEFAULT가 적용됩니다. "
                    + "보호자 모드 인증이 필요합니다.",
            parameters = @Parameter(
                    name = "X-Parent-Mode-Token",
                    description = "보호자 PIN 인증 후 발급받은 Parent Mode Token",
                    required = true,
                    in = ParameterIn.HEADER
            ),
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "아이 프로필 추가 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = CREATE_SUCCESS_EXAMPLE))),
            @ApiResponse(responseCode = "400", description = "필수값 누락, 잘못된 JSON 또는 입력값 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = {
                                    @ExampleObject(name = "잘못된 요청", value = INVALID_REQUEST_EXAMPLE),
                                    @ExampleObject(name = "잘못된 별명", value = INVALID_NICKNAME_EXAMPLE),
                                    @ExampleObject(name = "잘못된 나이 구간", value = INVALID_AGE_EXAMPLE),
                                    @ExampleObject(name = "잘못된 모야 유형", value = INVALID_MOYA_EXAMPLE)
                            })),
            @ApiResponse(responseCode = "401", description = "Access Token 누락 또는 유효하지 않은 토큰",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = AUTHENTICATION_REQUIRED_EXAMPLE))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_EXAMPLE)))
    })
    @ParentModeRequired
    @PostMapping
    public ResponseEntity<BaseResponse<ChildResponse>> createChild(
            Principal principal,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreateChildRequest.class),
                            examples = {
                                    @ExampleObject(name = "모야 유형 지정", value = CREATE_REQUEST_EXAMPLE),
                                    @ExampleObject(name = "모야 유형 생략 (DEFAULT)", value = DEFAULT_MOYA_REQUEST_EXAMPLE)
                            }))
            @RequestBody CreateChildRequest request
    ) {
        ChildResponse response = childService.createChild(Long.valueOf(principal.getName()), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.created("아이 프로필이 추가되었습니다.", response));
    }
}

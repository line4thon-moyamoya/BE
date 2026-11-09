package com.moyamoya.domain.user.controller;

import com.moyamoya.domain.user.dto.response.UserSettingsResponse;
import com.moyamoya.domain.user.service.UserSettingsService;
import com.moyamoya.global.common.BaseResponse;
import com.moyamoya.security.parentmode.ParentModeRequired;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사용자 설정", description = "사용자 설정 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/settings")
public class UserSettingsController {
    private final UserSettingsService userSettingsService;

    @Operation(summary = "설정 화면 조회", description = "설정 화면에 필요한 전체 아이 프로필 목록과 음성 동의 상태를 조회합니다. 등록된 아이가 없으면 빈 배열을 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth"),
            parameters = @Parameter(name = "X-Parent-Mode-Token", description = "보호자 PIN 인증으로 발급받은 보호자 모드 토큰", required = true, in = ParameterIn.HEADER))
    @ParentModeRequired
    @GetMapping
    public ResponseEntity<BaseResponse<UserSettingsResponse>> getSettings(Principal principal) {
        return ResponseEntity.ok(BaseResponse.success("설정 정보를 조회했습니다.",
                userSettingsService.getSettings(Long.valueOf(principal.getName()))));
    }
}

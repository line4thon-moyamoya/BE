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

@Tag(name = "User Settings", description = "User settings API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/settings")
public class UserSettingsController {
    private final UserSettingsService userSettingsService;

    @Operation(summary = "Get user settings", description = "Returns child profiles and voice consent status; children may be empty.",
            security = @SecurityRequirement(name = "bearerAuth"),
            parameters = @Parameter(name = "X-Parent-Mode-Token", required = true, in = ParameterIn.HEADER))
    @ParentModeRequired
    @GetMapping
    public ResponseEntity<BaseResponse<UserSettingsResponse>> getSettings(Principal principal) {
        return ResponseEntity.ok(BaseResponse.success("설정 정보를 조회했습니다.",
                userSettingsService.getSettings(Long.valueOf(principal.getName()))));
    }
}

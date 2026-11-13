package com.moyamoya.domain.report.controller;

import com.moyamoya.domain.report.dto.request.WeeklyReportPreviewRequest;
import com.moyamoya.domain.report.dto.response.WeeklyReportPreviewResponse;
import com.moyamoya.domain.report.service.WeeklyReportService;
import com.moyamoya.global.common.BaseResponse;
import com.moyamoya.security.parentmode.ParentModeRequired;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequiredArgsConstructor
@Profile("dev")
@RequestMapping("/api/v1/reports/weekly/test")
public class WeeklyReportTestController {

    private final WeeklyReportService weeklyReportService;

    @Operation(
            summary = "주간 리포트 미리보기(테스트)",
            description = "지정 기간 집계와 대화거리를 미리보기로 조회합니다. DB에 저장하지 않습니다.",
            parameters = @Parameter(
                    name = "X-Parent-Mode-Token",
                    description = "보호자 PIN 인증 후 발급받은 Parent Mode Token",
                    required = true,
                    in = ParameterIn.HEADER
            ),
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ParentModeRequired
    @PostMapping("/preview")
    public ResponseEntity<BaseResponse<WeeklyReportPreviewResponse>> preview(
            Principal principal,
            @Valid @RequestBody WeeklyReportPreviewRequest request
    ) {
        Long userId = Long.valueOf(principal.getName());

        WeeklyReportPreviewResponse response =
                weeklyReportService.previewForGuardian(
                        userId,
                        request.childId(),
                        request.startDate(),
                        request.endDate()
                );

        return ResponseEntity.ok(
                BaseResponse.success(
                        "주간 리포트 미리보기를 조회하였습니다.",
                        response
                )
        );
    }
}

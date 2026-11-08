package com.moyamoya.domain.song.controller;

import com.moyamoya.domain.song.dto.response.SongListResponse;
import com.moyamoya.domain.song.service.SongService;
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
@RequestMapping("/api/v1/children/{childId}/songs")
@Tag(name = "Song", description = "노래 API")
public class SongController {
    private final SongService songService;

    @Operation(summary = "노래 목록 조회",
            description = "아이가 보유한 노래 목록과 각 노래에 포함된 단어 5개를 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "노래 목록 조회 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":true,"code":200,"message":"노래 목록을 조회했습니다.",
                             "data":{"songs":[{"songId":1,"title":"우리 노래 1",
                             "words":["영수증","소방차","용기","적금","통장"]}]}}
                            """))),
            @ApiResponse(responseCode = "400", description = "CHILD400: 올바르지 않은 아이 ID"),
            @ApiResponse(responseCode = "401", description = "AUTH401: 로그인 필요"),
            @ApiResponse(responseCode = "403", description = "CHILD403: 접근 권한 없음"),
            @ApiResponse(responseCode = "404", description = "CHILD404: 아이 없음"),
            @ApiResponse(responseCode = "500", description = "COMMON500: 서버 오류")
    })
    @GetMapping
    public ResponseEntity<BaseResponse<SongListResponse>> getSongs(
            Principal principal,
            @Parameter(description = "노래 목록을 조회할 아이 ID", example = "1", required = true)
            @PathVariable("childId") Long childId
    ) {
        SongListResponse response = songService.getSongs(Long.valueOf(principal.getName()), childId);
        return ResponseEntity.ok(BaseResponse.success("노래 목록을 조회했습니다.", response));
    }
}

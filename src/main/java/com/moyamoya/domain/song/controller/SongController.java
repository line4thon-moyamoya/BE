package com.moyamoya.domain.song.controller;

import com.moyamoya.domain.song.dto.response.SongListResponse;
import com.moyamoya.domain.song.dto.response.SongDetailResponse;
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

    @Operation(summary = "노래 상세 및 생성 상태 조회",
            description = "노래 생성 상태를 조회하고, 생성이 완료되면 음원 URL과 구성 단어 정보를 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "노래 상세 및 생성 상태 조회 성공",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "생성 완료", value = """
                                    {"success":true,"code":200,"message":"노래 상세 정보를 조회했습니다.",
                                     "data":{"songId":12,"title":"우리 노래 1","status":"READY",
                                     "audioUrl":"https://example.com/songs/12.wav",
                                     "words":[{"wordId":1,"word":"영수증","imageUrl":null,"description":"물건을 사고 받은 종이야"},
                                     {"wordId":2,"word":"소방차","imageUrl":null,"description":"불을 끄러 가는 차야"},
                                     {"wordId":3,"word":"용기","imageUrl":null,"description":"무서워도 해보는 마음이야"},
                                     {"wordId":4,"word":"적금","imageUrl":null,"description":"돈을 조금씩 모으는 거야"},
                                     {"wordId":5,"word":"통장","imageUrl":null,"description":"은행에 맡긴 돈을 기록한 책이야"}]}}
                                    """),
                            @ExampleObject(name = "생성 중", value = """
                                    {"success":true,"code":200,"message":"노래를 생성하고 있습니다.",
                                     "data":{"songId":12,"title":"우리 노래 1","status":"GENERATING","audioUrl":null,"words":[]}}
                                    """),
                            @ExampleObject(name = "생성 실패", value = """
                                    {"success":true,"code":200,"message":"노래 생성에 실패했습니다.",
                                     "data":{"songId":12,"title":"우리 노래 1","status":"FAILED","audioUrl":null,"words":[]}}
                                    """)
                    })),
            @ApiResponse(responseCode = "400", description = "CHILD400 또는 SONG400: 올바르지 않은 ID"),
            @ApiResponse(responseCode = "401", description = "AUTH401: 로그인 필요"),
            @ApiResponse(responseCode = "403", description = "CHILD403: 접근 권한 없음"),
            @ApiResponse(responseCode = "404", description = "CHILD404: 아이 없음, SONG404: 해당 아이의 노래 없음"),
            @ApiResponse(responseCode = "500", description = "COMMON500: 서버 오류")
    })
    @GetMapping("/{songId}")
    public ResponseEntity<BaseResponse<SongDetailResponse>> getSongDetail(
            Principal principal,
            @Parameter(description = "아이 ID", example = "1", required = true)
            @PathVariable("childId") Long childId,
            @Parameter(description = "조회할 노래 ID", example = "12", required = true)
            @PathVariable("songId") Long songId
    ) {
        SongDetailResponse response = songService.getSongDetail(Long.valueOf(principal.getName()), childId, songId);
        String message = switch (response.status()) {
            case GENERATING -> "노래를 생성하고 있습니다.";
            case READY -> "노래 상세 정보를 조회했습니다.";
            case FAILED -> "노래 생성에 실패했습니다.";
        };
        return ResponseEntity.ok(BaseResponse.success(message, response));
    }
}

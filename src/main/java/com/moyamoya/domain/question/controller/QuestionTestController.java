package com.moyamoya.domain.question.controller;

import com.moyamoya.domain.question.dto.request.TestSttRequest;
import com.moyamoya.domain.question.dto.response.QuestionCandidateResponse;
import com.moyamoya.domain.question.service.QuestionService;
import com.moyamoya.global.common.BaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Profile({"local", "dev"})
@RequestMapping("/api/v1/questions/test")
public class QuestionTestController {

    private final QuestionService questionService;

    @PostMapping("/stt")
    public ResponseEntity<BaseResponse<QuestionCandidateResponse>> testStt(
            @RequestBody TestSttRequest request
    ) {
        QuestionCandidateResponse response = questionService.testStt(
                request.childId(),
                request.recognizedSentence()
        );

        return ResponseEntity.ok(
                BaseResponse.success("질문 후보 탐색이 완료되었습니다.", response)
        );
    }
}

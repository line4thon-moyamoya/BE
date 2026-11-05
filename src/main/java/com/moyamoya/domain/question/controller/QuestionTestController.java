package com.moyamoya.domain.question.controller;

import com.moyamoya.domain.question.dto.request.SecondCandidateTestRequest;
import com.moyamoya.domain.question.dto.request.TestSttRequest;
import com.moyamoya.domain.question.dto.response.QuestionCandidateResponse;
import com.moyamoya.domain.question.model.WordCandidate;
import com.moyamoya.domain.question.service.CandidateRankingService;
import com.moyamoya.domain.question.service.QuestionService;
import com.moyamoya.global.common.BaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Profile({"local", "dev"})
@RequestMapping("/api/v1/questions/test")
public class QuestionTestController {

    private final QuestionService questionService;
    private final CandidateRankingService candidateRankingService;

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

    @PostMapping("/candidates/second")
    public ResponseEntity<BaseResponse<List<WordCandidate>>> testSecond(
            @RequestBody SecondCandidateTestRequest request
    ) {
        List<WordCandidate> candidates = candidateRankingService.rankSecond(
                request.targetExpressions(),
                request.originalText(),
                request.heardFrom(),
                request.excludedCandidateKeys()
        );

        return ResponseEntity.ok(
                BaseResponse.success("2차 후보 탐색이 완료되었습니다.", candidates)
        );
    }
}

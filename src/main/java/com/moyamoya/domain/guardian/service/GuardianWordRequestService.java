package com.moyamoya.domain.guardian.service;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.exception.ChildErrorCode;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.guardian.dto.response.GuardianWordRequestResponse;
import com.moyamoya.domain.guardian.dto.response.ResolveWordResponse;
import com.moyamoya.domain.guardian.exception.GuardianErrorCode;
import com.moyamoya.domain.word.entity.WordRequest;
import com.moyamoya.domain.word.entity.WordRequestStatus;
import com.moyamoya.domain.word.repository.WordRequestRepository;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.s3.S3AudioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GuardianWordRequestService {

    private final ChildRepository childRepository;
    private final WordRequestRepository wordRequestRepository;
    private final S3AudioService s3AudioService;

    // 물어볼 단어 전체 목록 조회 (PENDING + RESOLVED)
    @Transactional(readOnly = true)
    public List<GuardianWordRequestResponse> getWordRequests(
            Long userId,
            Long childId
    ) {
        validateChildOwnership(userId, childId);

        return wordRequestRepository
                .findByChildIdOrderByCreatedAtDesc(childId)
                .stream()
                .map(request -> {
                    // 프론트 재생용 Presigned URL로 변환
                    String audioUrl = request.getAudioUrl() == null
                            ? null
                            : s3AudioService.getPresignedUrl(
                            request.getAudioUrl()
                    );

                    return GuardianWordRequestResponse.from(
                            request,
                            audioUrl
                    );
                })
                .toList();
    }

    // 보호자가 실제 단어를 알려주어 요청 해결
    @Transactional
    public ResolveWordResponse resolveWordRequest(
            Long userId,
            Long wordRequestId,
            String word
    ) {
        if (word == null || word.isBlank()
                || word.trim().length() > 100) {
            throw new CustomException(
                    GuardianErrorCode.INVALID_WORD_REQUEST
            );
        }

        WordRequest request = wordRequestRepository
                .findById(wordRequestId)
                .orElseThrow(() -> new CustomException(
                        GuardianErrorCode.WORD_REQUEST_NOT_FOUND
                ));

        // 다른 보호자의 아이 요청은 수정할 수 없음
        validateChildOwnership(
                userId,
                request.getChild().getId()
        );

        // 이미 해결한 요청은 재처리하지 않음
        if (request.getStatus() != WordRequestStatus.PENDING) {
            throw new CustomException(
                    GuardianErrorCode.WORD_REQUEST_ALREADY_RESOLVED
            );
        }

        request.resolve(word.trim());

        return ResolveWordResponse.from(request);
    }

    // 아이 소유권 검증
    private void validateChildOwnership(Long userId, Long childId) {
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new CustomException(
                        ChildErrorCode.CHILD_NOT_FOUND
                ));

        if (userId == null
                || !child.getUser().getId().equals(userId)) {
            throw new CustomException(
                    GuardianErrorCode.ACCESS_FORBIDDEN
            );
        }
    }
}

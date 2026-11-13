package com.moyamoya.domain.guardian.service;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.exception.ChildErrorCode;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.guardian.exception.GuardianErrorCode;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.word.entity.WordRequest;
import com.moyamoya.domain.word.entity.WordRequestStatus;
import com.moyamoya.domain.word.repository.WordRequestRepository;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.s3.S3Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class GuardianWordRequestServiceTest {

    private ChildRepository childRepository;
    private WordRequestRepository wordRequestRepository;
    private S3Service s3Service;

    private GuardianWordRequestService guardianWordRequestService;

    private static final Long USER_ID = 1L;
    private static final Long CHILD_ID = 2L;
    private static final Long WORD_REQUEST_ID = 31L;

    @BeforeEach
    void setUp() {
        childRepository = mock(ChildRepository.class);
        wordRequestRepository = mock(WordRequestRepository.class);
        s3Service = mock(S3Service.class);

        guardianWordRequestService = new GuardianWordRequestService(
                childRepository,
                wordRequestRepository,
                s3Service
        );
    }

    // 물어볼 단어 목록 조회 성공
    @Test
    void getWordRequestsReturnsPendingAndResolvedRequests() {
        stubChildOwnership(USER_ID, CHILD_ID);

        WordRequest pending = mockWordRequest(
                31L, "저끔", null,
                "questions/original/2/31.wav",
                WordRequestStatus.PENDING
        );

        WordRequest resolved = mockWordRequest(
                28L, "데금", "대금",
                null,
                WordRequestStatus.RESOLVED
        );

        when(wordRequestRepository
                .findByChildIdOrderByCreatedAtDesc(CHILD_ID))
                .thenReturn(List.of(pending, resolved));

        when(s3Service.getPresignedUrl(
                "questions/original/2/31.wav"
        )).thenReturn("https://example.com/audio.wav");

        var response = guardianWordRequestService.getWordRequests(
                USER_ID, CHILD_ID
        );

        assertThat(response).hasSize(2);

        assertThat(response.get(0).wordRequestId()).isEqualTo(31L);
        assertThat(response.get(0).recognizedText()).isEqualTo("저끔");
        assertThat(response.get(0).status()).isEqualTo("PENDING");
        assertThat(response.get(0).audioUrl())
                .isEqualTo("https://example.com/audio.wav");

        assertThat(response.get(1).wordRequestId()).isEqualTo(28L);
        assertThat(response.get(1).correctedWord()).isEqualTo("대금");
        assertThat(response.get(1).status()).isEqualTo("RESOLVED");
        assertThat(response.get(1).audioUrl()).isNull();

        verify(wordRequestRepository)
                .findByChildIdOrderByCreatedAtDesc(CHILD_ID);
    }

    // 원본 음성이 없는 경우 S3 URL 생성하지 않음
    @Test
    void getWordRequestsDoesNotGenerateUrlWhenAudioIsNull() {
        stubChildOwnership(USER_ID, CHILD_ID);

        WordRequest request = mockWordRequest(
                31L, "저끔", null,
                null,
                WordRequestStatus.PENDING
        );

        when(wordRequestRepository
                .findByChildIdOrderByCreatedAtDesc(CHILD_ID))
                .thenReturn(List.of(request));

        var response = guardianWordRequestService.getWordRequests(
                USER_ID, CHILD_ID
        );

        assertThat(response).hasSize(1);
        assertThat(response.get(0).audioUrl()).isNull();

        verifyNoInteractions(s3Service);
    }

    // 요청 목록이 없으면 빈 리스트 반환
    @Test
    void getWordRequestsReturnsEmptyList() {
        stubChildOwnership(USER_ID, CHILD_ID);

        when(wordRequestRepository
                .findByChildIdOrderByCreatedAtDesc(CHILD_ID))
                .thenReturn(List.of());

        var response = guardianWordRequestService.getWordRequests(
                USER_ID, CHILD_ID
        );

        assertThat(response).isEmpty();
    }

    // 존재하지 않는 아이 조회
    @Test
    void getWordRequestsThrowsWhenChildNotFound() {
        when(childRepository.findById(CHILD_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                guardianWordRequestService.getWordRequests(
                        USER_ID, CHILD_ID
                ))
                .isInstanceOf(CustomException.class)
                .extracting(ex ->
                        ((CustomException) ex).getErrorCode()
                )
                .isEqualTo(ChildErrorCode.CHILD_NOT_FOUND);

        verifyNoInteractions(wordRequestRepository);
    }

    // 다른 보호자의 아이 조회
    @Test
    void getWordRequestsThrowsWhenNotOwner() {
        stubChildOwnership(999L, CHILD_ID);

        assertThatThrownBy(() ->
                guardianWordRequestService.getWordRequests(
                        USER_ID, CHILD_ID
                ))
                .isInstanceOf(CustomException.class)
                .extracting(ex ->
                        ((CustomException) ex).getErrorCode()
                )
                .isEqualTo(GuardianErrorCode.ACCESS_FORBIDDEN);

        verifyNoInteractions(wordRequestRepository);
    }

    // 물어볼 단어 해결 성공
    @Test
    void resolveWordRequestSuccess() {
        stubChildOwnership(USER_ID, CHILD_ID);

        Child child = childRepository.findById(CHILD_ID).orElseThrow();

        WordRequest request = WordRequest.create(
                child,
                "저끔",
                "questions/original/2/31.wav"
        );

        when(wordRequestRepository.findById(WORD_REQUEST_ID))
                .thenReturn(Optional.of(request));

        var response = guardianWordRequestService.resolveWordRequest(
                USER_ID,
                WORD_REQUEST_ID,
                "  적금  "
        );

        assertThat(request.getCorrectedWord()).isEqualTo("적금");
        assertThat(request.getStatus())
                .isEqualTo(WordRequestStatus.RESOLVED);
        assertThat(request.getResolvedAt()).isNotNull();

        assertThat(response.correctedWord()).isEqualTo("적금");
        assertThat(response.status()).isEqualTo("RESOLVED");
    }

    // 빈 단어 입력
    @Test
    void resolveWordRequestThrowsWhenWordIsBlank() {
        assertThatThrownBy(() ->
                guardianWordRequestService.resolveWordRequest(
                        USER_ID, WORD_REQUEST_ID, "   "
                ))
                .isInstanceOf(CustomException.class)
                .extracting(ex ->
                        ((CustomException) ex).getErrorCode()
                )
                .isEqualTo(
                        GuardianErrorCode.INVALID_WORD_REQUEST
                );

        verifyNoInteractions(wordRequestRepository);
    }

    // 100자 초과 단어 입력
    @Test
    void resolveWordRequestThrowsWhenWordTooLong() {
        String longWord = "가".repeat(101);

        assertThatThrownBy(() ->
                guardianWordRequestService.resolveWordRequest(
                        USER_ID, WORD_REQUEST_ID, longWord
                ))
                .isInstanceOf(CustomException.class)
                .extracting(ex ->
                        ((CustomException) ex).getErrorCode()
                )
                .isEqualTo(
                        GuardianErrorCode.INVALID_WORD_REQUEST
                );

        verifyNoInteractions(wordRequestRepository);
    }

    // 존재하지 않는 물어볼 단어 요청
    @Test
    void resolveWordRequestThrowsWhenRequestNotFound() {
        when(wordRequestRepository.findById(WORD_REQUEST_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                guardianWordRequestService.resolveWordRequest(
                        USER_ID, WORD_REQUEST_ID, "적금"
                ))
                .isInstanceOf(CustomException.class)
                .extracting(ex ->
                        ((CustomException) ex).getErrorCode()
                )
                .isEqualTo(
                        GuardianErrorCode.WORD_REQUEST_NOT_FOUND
                );
    }

    // 다른 보호자의 단어 요청 해결 방지
    @Test
    void resolveWordRequestThrowsWhenNotOwner() {
        stubChildOwnership(999L, CHILD_ID);

        WordRequest request = mock(WordRequest.class);
        Child child = childRepository.findById(CHILD_ID).orElseThrow();

        when(request.getChild()).thenReturn(child);
        when(wordRequestRepository.findById(WORD_REQUEST_ID))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() ->
                guardianWordRequestService.resolveWordRequest(
                        USER_ID, WORD_REQUEST_ID, "적금"
                ))
                .isInstanceOf(CustomException.class)
                .extracting(ex ->
                        ((CustomException) ex).getErrorCode()
                )
                .isEqualTo(GuardianErrorCode.ACCESS_FORBIDDEN);

        verify(request, never()).resolve(anyString());
    }

    // 이미 해결된 요청 재처리 방지
    @Test
    void resolveWordRequestThrowsWhenAlreadyResolved() {
        stubChildOwnership(USER_ID, CHILD_ID);

        WordRequest request = mock(WordRequest.class);
        Child child = childRepository.findById(CHILD_ID).orElseThrow();

        when(request.getChild()).thenReturn(child);
        when(request.getStatus())
                .thenReturn(WordRequestStatus.RESOLVED);

        when(wordRequestRepository.findById(WORD_REQUEST_ID))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() ->
                guardianWordRequestService.resolveWordRequest(
                        USER_ID, WORD_REQUEST_ID, "적금"
                ))
                .isInstanceOf(CustomException.class)
                .extracting(ex ->
                        ((CustomException) ex).getErrorCode()
                )
                .isEqualTo(
                        GuardianErrorCode.WORD_REQUEST_ALREADY_RESOLVED
                );

        verify(request, never()).resolve(anyString());
    }

    // 테스트용 아이 소유권 설정
    private void stubChildOwnership(Long ownerId, Long childId) {
        Child child = mock(Child.class);
        User user = mock(User.class);

        when(user.getId()).thenReturn(ownerId);
        when(child.getUser()).thenReturn(user);
        when(child.getId()).thenReturn(childId);

        when(childRepository.findById(childId))
                .thenReturn(Optional.of(child));
    }

    // 테스트용 WordRequest 생성
    private WordRequest mockWordRequest(
            Long id,
            String recognizedText,
            String correctedWord,
            String audioUrl,
            WordRequestStatus status
    ) {
        WordRequest request = mock(WordRequest.class);

        when(request.getId()).thenReturn(id);
        when(request.getRecognizedText()).thenReturn(recognizedText);
        when(request.getCorrectedWord()).thenReturn(correctedWord);
        when(request.getAudioUrl()).thenReturn(audioUrl);
        when(request.getStatus()).thenReturn(status);
        when(request.getCreatedAt())
                .thenReturn(LocalDateTime.of(2026, 11, 7, 10, 30));

        if (status == WordRequestStatus.RESOLVED) {
            when(request.getResolvedAt())
                    .thenReturn(LocalDateTime.of(2026, 11, 7, 11, 0));
        }

        return request;
    }
}


package com.moyamoya.domain.question.service;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.question.dto.response.WordRequestSaveResponse;
import com.moyamoya.domain.question.exception.QuestionErrorCode;
import com.moyamoya.domain.question.session.QuestionSession;
import com.moyamoya.domain.question.session.QuestionSessionStore;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.word.entity.HeardFrom;
import com.moyamoya.domain.word.entity.WordRequest;
import com.moyamoya.domain.word.entity.WordRequestStatus;
import com.moyamoya.domain.word.repository.WordRequestRepository;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.s3.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WordRequestSaveServiceTest {

    private QuestionSessionStore sessionStore;
    private QuestionAudioTempService audioTempService;
    private S3Service s3Service;
    private WordRequestRepository wordRequestRepository;
    private ChildRepository childRepository;
    private TransactionTemplate transactionTemplate;

    private WordRequestSaveService wordRequestSaveService;

    @BeforeEach
    void setUp() {
        sessionStore = mock(QuestionSessionStore.class);
        audioTempService = mock(QuestionAudioTempService.class);
        s3Service = mock(S3Service.class);
        wordRequestRepository = mock(WordRequestRepository.class);
        childRepository = mock(ChildRepository.class);
        transactionTemplate = mock(TransactionTemplate.class);

        when(transactionTemplate.execute(any()))
                .thenAnswer(invocation ->
                        ((TransactionCallback<?>) invocation.getArgument(0))
                                .doInTransaction(null)
                );

        wordRequestSaveService = new WordRequestSaveService(
                sessionStore,
                audioTempService,
                s3Service,
                wordRequestRepository,
                childRepository,
                transactionTemplate
        );
    }

    @Test
    void saveUploadsWavWithWavExtensionAndContentType() {
        assertSavedWith("audio/wav", wavBytes(), "wav");
    }

    @Test
    void saveUploadsMp3WithMp3ExtensionAndContentType() {
        assertSavedWith("audio/mpeg", mp3Bytes(), "mp3");
    }

    @Test
    void saveUploadsWebmWithWebmExtensionAndContentType() {
        assertSavedWith("audio/webm", webmBytes(), "webm");
    }

    @Test
    void saveUploadsM4aWithM4aExtensionAndContentType() {
        assertSavedWith("audio/mp4", m4aBytes(), "m4a");
    }

    @Test
    void saveThrowsInvalidAudioFileWhenContentTypeAndBytesMismatch() {
        Long userId = 7L;
        QuestionSession session = readySession("audio/wav");
        UUID sessionId = session.getSessionId();
        Child child = childOwnedBy(userId);

        when(sessionStore.find(sessionId)).thenReturn(Optional.of(session));
        when(sessionStore.isCurrent(session)).thenReturn(true);
        when(childRepository.findById(2L)).thenReturn(Optional.of(child));
        when(audioTempService.read(session.getOriginalAudioTempPath()))
                .thenReturn(mp3Bytes());

        assertThatThrownBy(() -> wordRequestSaveService.save(userId, sessionId.toString()))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(QuestionErrorCode.INVALID_AUDIO_FILE);

        verify(s3Service, never()).uploadAudio(any(), any(), any());
        verify(wordRequestRepository, never()).save(any());
    }

    private void assertSavedWith(
            String contentType,
            byte[] bytes,
            String extension
    ) {
        Long userId = 7L;
        QuestionSession session = readySession(contentType);
        UUID sessionId = session.getSessionId();
        Child child = childOwnedBy(userId);

        when(sessionStore.find(sessionId)).thenReturn(Optional.of(session));
        when(sessionStore.isCurrent(session)).thenReturn(true);
        when(childRepository.findById(2L)).thenReturn(Optional.of(child));
        when(audioTempService.read(session.getOriginalAudioTempPath())).thenReturn(bytes);

        String expectedKey = "questions/original/2/" + sessionId + "." + extension;
        when(s3Service.uploadAudio(
                eq(expectedKey),
                eq(bytes),
                eq(contentType)
        )).thenReturn(expectedKey);

        WordRequest saved = mock(WordRequest.class);
        when(saved.getId()).thenReturn(11L);
        when(saved.getStatus()).thenReturn(WordRequestStatus.PENDING);
        when(wordRequestRepository.save(any())).thenReturn(saved);

        WordRequestSaveResponse response = wordRequestSaveService.save(
                userId,
                sessionId.toString()
        );

        assertThat(response.wordRequestId()).isEqualTo(11L);
        assertThat(response.status()).isEqualTo("PENDING");

        verify(s3Service).uploadAudio(expectedKey, bytes, contentType);
        verify(sessionStore).deleteTempAudio(session);
    }

    private QuestionSession readySession(String contentType) {
        QuestionSession session = QuestionSession.create(2L, "적금이 뭐야", null);
        session.attachOriginalAudio(
                Path.of("C:\\temp\\audio.tmp"),
                contentType
        );
        session.setExtractionResult("적금", List.of("적금"));
        session.setFirstCandidates(List.of());
        session.setSecondCandidates(HeardFrom.PARENTS, List.of());
        return session;
    }

    private Child childOwnedBy(Long userId) {
        Child child = mock(Child.class);
        User user = mock(User.class);
        when(child.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(userId);
        return child;
    }

    private byte[] wavBytes() {
        return new byte[]{
                'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00,
                'W', 'A', 'V', 'E', 'f', 'm', 't', ' '
        };
    }

    private byte[] mp3Bytes() {
        return new byte[]{
                'I', 'D', '3', 0x04, 0x00, 0x00, 0x00
        };
    }

    private byte[] webmBytes() {
        return new byte[]{
                0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0x42, (byte) 0x82
        };
    }

    private byte[] m4aBytes() {
        return new byte[]{
                0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p',
                'M', '4', 'A', ' ', 0x00, 0x00, 0x00, 0x00
        };
    }
}

package com.moyamoya.domain.question.service;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.question.exception.QuestionErrorCode;
import com.moyamoya.domain.question.model.WordCandidate;
import com.moyamoya.domain.question.session.QuestionSession;
import com.moyamoya.domain.question.session.QuestionSessionStore;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.entity.WordSourceType;
import com.moyamoya.domain.word.repository.QuestionHistoryRepository;
import com.moyamoya.domain.word.repository.WordRepository;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.s3.S3AudioService;
import com.moyamoya.domain.question.session.QuestionSessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionConfirmServiceTest {

    private QuestionSessionStore sessionStore;
    private ChildRepository childRepository;
    private WordRepository wordRepository;
    private QuestionHistoryRepository questionHistoryRepository;
    private CandidateTtsService candidateTtsService;
    private S3AudioService s3AudioService;

    private QuestionConfirmService questionConfirmService;

    @BeforeEach
    void setUp() {
        sessionStore = mock(QuestionSessionStore.class);
        childRepository = mock(ChildRepository.class);
        wordRepository = mock(WordRepository.class);
        questionHistoryRepository = mock(QuestionHistoryRepository.class);
        candidateTtsService = mock(CandidateTtsService.class);
        s3AudioService = mock(S3AudioService.class);

        questionConfirmService = new QuestionConfirmService(
                sessionStore,
                childRepository,
                wordRepository,
                questionHistoryRepository,
                candidateTtsService,
                s3AudioService
        );
    }

    @Test
    void confirmWordThrowsInvalidCandidateWhenCandidateNotFound() {
        QuestionSession session = QuestionSession.create(1L, "적금이 뭐야", null);
        session.setExtractionResult("적금", List.of("적금"));
        session.setFirstCandidates(List.of(new WordCandidate(
                "CURATED:적금",
                null,
                "적금",
                WordSourceType.CURATED,
                0.9,
                0.0,
                0.9,
                "정의",
                null,
                null,
                null
        )));

        when(sessionStore.find(session.getSessionId())).thenReturn(Optional.of(session));
        stubChildOwnership(1L, 1L);

        assertThatThrownBy(() -> questionConfirmService.confirmWord(
                1L,
                session.getSessionId().toString(),
                "c_999"
        ))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(QuestionErrorCode.INVALID_CANDIDATE);
    }

    @Test
    void confirmWordThrowsTtsGenerationFailedWhenStoredAudioFails() {
        QuestionSession session = QuestionSession.create(1L, "적금이 뭐야", null);
        session.setExtractionResult("적금", List.of("적금"));
        session.setFirstCandidates(List.of(new WordCandidate(
                "CURATED:적금",
                null,
                "적금",
                WordSourceType.CURATED,
                0.9,
                0.0,
                0.9,
                "정의",
                null,
                null,
                null
        )));

        Word existingWord = mock(Word.class);
        when(existingWord.getWord()).thenReturn("적금");

        when(sessionStore.find(session.getSessionId())).thenReturn(Optional.of(session));
        stubChildOwnership(1L, 1L);
        when(wordRepository.findByWord("적금")).thenReturn(Optional.of(existingWord));
        org.mockito.Mockito.doThrow(new IllegalStateException("tts failed"))
                .when(candidateTtsService)
                .ensureStoredAudio(any(), any());

        assertThatThrownBy(() -> questionConfirmService.confirmWord(
                1L,
                session.getSessionId().toString(),
                "c_001"
        ))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(QuestionErrorCode.TTS_GENERATION_FAILED);
    }

    @Test
    void confirmWordSavesQuestionHistory() {
        QuestionSession session =
                QuestionSession.create(1L, "적금이 뭐야", null);

        session.setExtractionResult("적금", List.of("적금"));

        session.setFirstCandidates(List.of(new WordCandidate(
                "CURATED:적금",
                10L,
                "적금",
                WordSourceType.CURATED,
                0.9, 0.0, 0.9,
                "돈을 모으는 방법",
                null, null, null
        )));

        Word word = mock(Word.class);
        when(word.getId()).thenReturn(10L);
        when(word.getWord()).thenReturn("적금");

        when(sessionStore.find(session.getSessionId()))
                .thenReturn(Optional.of(session));

        stubChildOwnership(1L, 1L);

        when(wordRepository.findByWord("적금"))
                .thenReturn(Optional.of(word));

        questionConfirmService.confirmWord(
                1L,
                session.getSessionId().toString(),
                "c_001"
        );

        assertThat(session.getStatus())
                .isEqualTo(QuestionSessionStatus.CONFIRMED);

        org.mockito.Mockito.verify(questionHistoryRepository)
                .save(any());
    }

    private void stubChildOwnership(Long userId, Long childId) {
        Child child = mock(Child.class);
        User user = mock(User.class);

        when(user.getId()).thenReturn(userId);
        when(child.getUser()).thenReturn(user);
        when(child.getAgeGroup()).thenReturn(AgeGroup.AGE_5);

        when(childRepository.findById(childId))
                .thenReturn(Optional.of(child));
    }
}

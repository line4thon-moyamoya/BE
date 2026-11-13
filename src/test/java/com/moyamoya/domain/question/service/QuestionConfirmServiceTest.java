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
import com.moyamoya.global.s3.S3Service;
import com.moyamoya.domain.question.session.QuestionSessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionConfirmServiceTest {

    private QuestionSessionStore sessionStore;
    private ChildRepository childRepository;
    private WordRepository wordRepository;
    private QuestionHistoryRepository questionHistoryRepository;
    private CandidateTtsService candidateTtsService;
    private S3Service s3Service;
    private TransactionTemplate transactionTemplate;
    private WordDescriptionService wordDescriptionService;

    private QuestionConfirmService questionConfirmService;

    @BeforeEach
    void setUp() {
        sessionStore = mock(QuestionSessionStore.class);
        childRepository = mock(ChildRepository.class);
        wordRepository = mock(WordRepository.class);
        questionHistoryRepository = mock(QuestionHistoryRepository.class);
        candidateTtsService = mock(CandidateTtsService.class);
        s3Service = mock(S3Service.class);
        transactionTemplate = mock(TransactionTemplate.class);

        // 신규 추가: 연령별 설명 생성 서비스
        wordDescriptionService = mock(WordDescriptionService.class);

        when(transactionTemplate.execute(any()))
                .thenAnswer(invocation ->
                        ((TransactionCallback<?>) invocation.getArgument(0))
                                .doInTransaction(null)
                );

        questionConfirmService = new QuestionConfirmService(
                sessionStore,
                childRepository,
                wordRepository,
                questionHistoryRepository,
                candidateTtsService,
                s3Service,
                transactionTemplate,
                wordDescriptionService
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
                null,
                null
        )));

        when(sessionStore.find(session.getSessionId())).thenReturn(Optional.of(session));
        when(sessionStore.isCurrent(session)).thenReturn(true);
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
                null,
                null
        )));

        Word existingWord = mock(Word.class);
        when(existingWord.getWord()).thenReturn("적금");

        when(sessionStore.find(session.getSessionId())).thenReturn(Optional.of(session));
        when(sessionStore.isCurrent(session)).thenReturn(true);
        stubChildOwnership(1L, 1L);
        when(wordRepository.findByWord("적금")).thenReturn(Optional.of(existingWord));
        org.mockito.Mockito.doThrow(new IllegalStateException("tts failed"))
                .when(candidateTtsService)
                .ensureStoredAudio(any());

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
    void confirmWordShouldGenerateOnlyCurrentAgeDescription() {
        QuestionSession session =
                QuestionSession.create(1L, "자외선이 뭐야?", null);

        session.setExtractionResult("자외선", List.of("자외선"));
        session.setFirstCandidates(List.of(new WordCandidate(
                "EXTERNAL:자외선",
                null,
                "자외선",
                WordSourceType.EXTERNAL,
                1.0,
                0.0,
                1.0,
                "눈에 보이지 않는 빛의 한 종류",
                null,
                null,
                null,
                null
        )));

        Word word = Word.createExternal(
                "자외선",
                "눈에 보이지 않는 빛의 한 종류",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        when(sessionStore.find(session.getSessionId()))
                .thenReturn(Optional.of(session));
        when(sessionStore.isCurrent(session)).thenReturn(true);
        stubChildOwnership(1L, 1L);
        when(wordRepository.findByWord("자외선"))
                .thenReturn(Optional.of(word));
        when(wordDescriptionService.generate(
                "자외선",
                "눈에 보이지 않는 빛의 한 종류",
                AgeGroup.AGE_5
        )).thenReturn("햇빛 속에 있는 눈에 보이지 않는 빛이야.");

        questionConfirmService.confirmWord(
                1L,
                session.getSessionId().toString(),
                "c_001"
        );

        verify(wordDescriptionService).generate(
                "자외선",
                "눈에 보이지 않는 빛의 한 종류",
                AgeGroup.AGE_5
        );
        verify(wordDescriptionService, never()).generate(
                eq("자외선"),
                eq("눈에 보이지 않는 빛의 한 종류"),
                eq(AgeGroup.AGE_3_4)
        );

        assertThat(word.getDescAge5())
                .isEqualTo("햇빛 속에 있는 눈에 보이지 않는 빛이야.");
        assertThat(word.getDescAge3To4()).isNull();
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
                null, null, null, null
        )));

        Word word = mock(Word.class);
        when(word.getId()).thenReturn(10L);
        when(word.getWord()).thenReturn("적금");
        when(word.getImageUrl()).thenReturn("words/images/10.png");
        when(candidateTtsService.resolvePresignedUrl("words/images/10.png"))
                .thenReturn("https://image.example/10.png");

        when(sessionStore.find(session.getSessionId()))
                .thenReturn(Optional.of(session));
        when(sessionStore.isCurrent(session)).thenReturn(true);

        stubChildOwnership(1L, 1L);

        when(wordRepository.findByWord("적금"))
                .thenReturn(Optional.of(word));

        var response = questionConfirmService.confirmWord(
                1L,
                session.getSessionId().toString(),
                "c_001"
        );

        assertThat(session.getStatus())
                .isEqualTo(QuestionSessionStatus.CONFIRMED);
        assertThat(response.imageUrl()).isEqualTo("https://image.example/10.png");

        verify(questionHistoryRepository)
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

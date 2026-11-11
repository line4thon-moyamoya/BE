package com.moyamoya.domain.question.service;

import com.moyamoya.domain.question.extractor.JosaCandidateGenerator;
import com.moyamoya.domain.question.exception.QuestionErrorCode;
import com.moyamoya.domain.question.extractor.QuestionTargetExtractor;
import com.moyamoya.domain.question.model.QuestionExtractionResult;
import com.moyamoya.domain.question.model.WordCandidate;
import com.moyamoya.domain.question.session.QuestionSession;
import com.moyamoya.domain.question.session.QuestionSessionStore;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.user.repository.UserRepository;
import com.moyamoya.domain.word.entity.HeardFrom;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.entity.WordSourceType;
import com.moyamoya.domain.word.repository.WordRepository;
import com.moyamoya.global.exception.CustomException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.moyamoya.domain.child.entity.AgeGroup;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionServiceTest {

    private QuestionTargetExtractor targetExtractor;
    private JosaCandidateGenerator josaGenerator;
    private CandidateRankingService rankingService;
    private SttService sttService;
    private CandidateTtsService candidateTtsService;
    private MoyaTtsService moyaTtsService;
    private QuestionSessionStore sessionStore;
    private WordRepository wordRepository;
    private ChildRepository childRepository;
    private UserRepository userRepository;
    private QuestionAudioTempService audioTempService;
    private QuestionService questionService;

    @BeforeEach
    void setUp() {
        targetExtractor = mock(QuestionTargetExtractor.class);
        josaGenerator = mock(JosaCandidateGenerator.class);
        rankingService = mock(CandidateRankingService.class);
        sttService = mock(SttService.class);
        candidateTtsService = mock(CandidateTtsService.class);
        moyaTtsService = mock(MoyaTtsService.class);
        sessionStore = mock(QuestionSessionStore.class);
        wordRepository = mock(WordRepository.class);
        childRepository = mock(ChildRepository.class);
        userRepository = mock(UserRepository.class);
        audioTempService = mock(QuestionAudioTempService.class);

        questionService = new QuestionService(
                targetExtractor,
                josaGenerator,
                rankingService,
                sttService,
                candidateTtsService,
                moyaTtsService,
                sessionStore,
                wordRepository,
                childRepository,
                userRepository,
                audioTempService
        );

        when(moyaTtsService.synthesize(any()))
                .thenReturn(new byte[]{1, 2, 3});
    }

    @Test
    void testSttFillsCandidateWordIdFromWordRepository() {
        when(targetExtractor.extract("엄마, 데금이 뭐야?"))
                .thenReturn(QuestionExtractionResult.success("엄마, 데금이 뭐야?", "데금이"));
        when(josaGenerator.generate("데금이"))
                .thenReturn(List.of("데금이", "데금"));
        when(rankingService.rankFirst(List.of("데금이", "데금")))
                .thenReturn(List.of(new WordCandidate(
                        "CURATED:세금",
                        null,
                        "세금",
                        WordSourceType.CURATED,
                        0.92,
                        0.0,
                        0.92,
                        "기존정의",
                        null,
                        null,
                        null,
                        null
                )));
        when(rankingService.resolveTargetPronunciation(any()))
                .thenReturn(Optional.empty());
        stubChildOwnership(1L, 1L);

        Word word = mock(Word.class);
        when(word.getId()).thenReturn(42L);
        when(word.getDefinition()).thenReturn("나라에 내는 돈");
        when(word.getDescAge3To4()).thenReturn("나라에 내는 돈(3-4)");
        when(word.getSourceType()).thenReturn(WordSourceType.CURATED);
        when(word.getImageUrl()).thenReturn("words/images/42.png");
        when(word.getWordAudioUrl())
                .thenReturn("words/audio/42/word.wav");
        when(candidateTtsService.resolveAudioUrl(
                "words/audio/42/word.wav"
        )).thenReturn("https://audio.example/word.wav");
        when(candidateTtsService.resolvePresignedUrl(
                "words/images/42.png"
        )).thenReturn("https://image.example/42.png");
        when(wordRepository.findByWord("세금")).thenReturn(Optional.of(word));

        var response = questionService.testStt(1L, "엄마, 데금이 뭐야?");

        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().getFirst().wordId()).isEqualTo(42L);
        assertThat(response.candidates().getFirst().description()).isEqualTo("나라에 내는 돈(3-4)");
        assertThat(response.candidates().getFirst().wordAudioUrl())
                .isEqualTo("https://audio.example/word.wav");
        assertThat(response.candidates().getFirst().imageUrl())
                .isEqualTo("https://image.example/42.png");
        assertThat(response.candidates().getFirst().candidateId()).isEqualTo("c_001");
        assertThat(response.dialogue()).isNotNull();
        assertThat(response.dialogue().audioContentType()).isEqualTo("audio/wav");
    }

    @Test
    void findSecondCandidatesUsesSessionDataAndExcludesFirstCandidates() {
        QuestionSession session = QuestionSession.create(1L, "엄마 저끔이 뭐야?", null);
        session.setExtractionResult("저끔", List.of("저끔"));
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

        List<WordCandidate> secondCandidates = List.of(new WordCandidate(
                "CURATED:연금",
                null,
                "연금",
                WordSourceType.CURATED,
                0.8,
                0.6,
                0.75,
                "정의2",
                null,
                null,
                null,
                null
        ));

        when(sessionStore.find(session.getSessionId())).thenReturn(Optional.of(session));
        stubChildOwnership(1L, 1L);
        when(rankingService.rankSecond(
                List.of("저끔"),
                "엄마 저끔이 뭐야?",
                HeardFrom.PARENTS,
                Set.of("CURATED:적금")
        )).thenReturn(secondCandidates);
        when(wordRepository.findByWord("연금")).thenReturn(Optional.empty());

        var response = questionService.findSecondCandidates(
                1L,
                session.getSessionId().toString(),
                HeardFrom.PARENTS
        );

        assertThat(response.status()).isEqualTo("CANDIDATES_FOUND");
        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().getFirst().candidateId()).isEqualTo("c_001");
        assertThat(response.dialogue()).isNotNull();
        assertThat(response.dialogue().audioContentType()).isEqualTo("audio/wav");
    }

    @Test
    void testSttThrowsTtsGenerationFailedWhenDialogueTtsFails() {
        when(targetExtractor.extract("엄마, 데금이 뭐야?"))
                .thenReturn(QuestionExtractionResult.success("엄마, 데금이 뭐야?", "데금이"));
        when(josaGenerator.generate("데금이"))
                .thenReturn(List.of("데금이", "데금"));
        when(rankingService.rankFirst(List.of("데금이", "데금")))
                .thenReturn(List.of(new WordCandidate(
                        "CURATED:세금",
                        null,
                        "세금",
                        WordSourceType.CURATED,
                        0.92,
                        0.0,
                        0.92,
                        "기존정의",
                        null,
                        null,
                        null
                )));
        when(rankingService.resolveTargetPronunciation(any()))
                .thenReturn(Optional.empty());
        stubChildOwnership(1L, 1L);
        when(wordRepository.findByWord("세금")).thenReturn(Optional.empty());
        when(moyaTtsService.synthesize(any()))
                .thenThrow(new IllegalStateException("tts failed"));

        assertThatThrownBy(() -> questionService.testStt(1L, "엄마, 데금이 뭐야?"))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(QuestionErrorCode.TTS_GENERATION_FAILED);
    }

    @Test
    void findSecondCandidatesThrowsTtsGenerationFailedWhenCandidateTtsFails() {
        QuestionSession session = QuestionSession.create(1L, "엄마 저끔이 뭐야?", null);
        session.setExtractionResult("저끔", List.of("저끔"));
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

        List<WordCandidate> secondCandidates = List.of(new WordCandidate(
                "CURATED:연금",
                null,
                "연금",
                WordSourceType.CURATED,
                0.8,
                0.6,
                0.75,
                "정의2",
                null,
                null,
                null,
                null
        ));

        Word existingWord = mock(Word.class);
        when(existingWord.getWordAudioUrl()).thenReturn("word-key");
        when(existingWord.getId()).thenReturn(100L);
        when(existingWord.getSourceType()).thenReturn(WordSourceType.CURATED);

        when(sessionStore.find(session.getSessionId())).thenReturn(Optional.of(session));
        stubChildOwnership(1L, 1L);
        when(rankingService.rankSecond(
                List.of("저끔"),
                "엄마 저끔이 뭐야?",
                HeardFrom.PARENTS,
                Set.of("CURATED:적금")
        )).thenReturn(secondCandidates);
        when(wordRepository.findByWord("연금")).thenReturn(Optional.of(existingWord));
        when(candidateTtsService.resolveAudioUrl(any())).thenReturn("https://audio.example");
        org.mockito.Mockito.doThrow(new IllegalStateException("tts failed"))
                .when(candidateTtsService)
                .ensureStoredAudio(any());

        assertThatThrownBy(() -> questionService.findSecondCandidates(
                1L,
                session.getSessionId().toString(),
                HeardFrom.PARENTS
        ))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(QuestionErrorCode.TTS_GENERATION_FAILED);
    }

    @Test
    void findSecondCandidatesKeepsSessionStateWhenDialogueTtsFails() {
        QuestionSession session = QuestionSession.create(1L, "엄마 저끔이 뭐야?", null);
        session.setExtractionResult("저끔", List.of("저끔"));
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

        List<WordCandidate> secondCandidates = List.of(new WordCandidate(
                "CURATED:연금",
                null,
                "연금",
                WordSourceType.CURATED,
                0.8,
                0.6,
                0.75,
                "정의2",
                null,
                null,
                null,
                null
        ));

        when(sessionStore.find(session.getSessionId())).thenReturn(Optional.of(session));
        stubChildOwnership(1L, 1L);
        when(rankingService.rankSecond(
                List.of("저끔"),
                "엄마 저끔이 뭐야?",
                HeardFrom.PARENTS,
                Set.of("CURATED:적금")
        )).thenReturn(secondCandidates);
        when(wordRepository.findByWord("연금")).thenReturn(Optional.empty());
        when(moyaTtsService.synthesize(any()))
                .thenThrow(new IllegalStateException("tts failed"));

        assertThatThrownBy(() -> questionService.findSecondCandidates(
                1L,
                session.getSessionId().toString(),
                HeardFrom.PARENTS
        ))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(QuestionErrorCode.TTS_GENERATION_FAILED);

        assertThat(session.getStatus()).isEqualTo(com.moyamoya.domain.question.session.QuestionSessionStatus.FIRST_CANDIDATES_READY);
        verify(sessionStore, never()).save(session);
    }

    @Test
    void analyzeAudioThrowsAudioTempSaveFailedWhenTempSaveFails() {
        MultipartFile audio = new MockMultipartFile(
                "audio",
                "test.wav",
                "audio/wav",
                new byte[]{
                        'R', 'I', 'F', 'F',
                        0x24, 0x00, 0x00, 0x00,
                        'W', 'A', 'V', 'E',
                        'f', 'm', 't', ' '
                }
        );
        when(audioTempService.save(audio))
                .thenThrow(new IllegalStateException("temp save failed"));
        stubChildOwnership(1L, 1L);

        assertThatThrownBy(() -> questionService.analyzeAudio(1L, 1L, audio))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(QuestionErrorCode.AUDIO_TEMP_SAVE_FAILED);
    }

    private void stubChildOwnership(Long userId, Long childId) {
        Child child = mock(Child.class);
        User user = mock(User.class);

        when(user.getId()).thenReturn(userId);
        when(child.getUser()).thenReturn(user);

        when(child.getAgeGroup()).thenReturn(AgeGroup.AGE_3_4);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(childRepository.findById(childId))
                .thenReturn(Optional.of(child));
    }
}

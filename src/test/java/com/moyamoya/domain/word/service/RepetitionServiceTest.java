package com.moyamoya.domain.word.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.question.config.QuestionDataLoader;
import com.moyamoya.domain.question.exception.QuestionErrorCode;
import com.moyamoya.domain.question.service.PronunciationSimilarity;
import com.moyamoya.domain.question.service.SttService;
import com.moyamoya.domain.question.service.MoyaTtsService;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.word.entity.LearnedWord;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.exception.WordErrorCode;
import com.moyamoya.domain.word.repository.LearnedWordRepository;
import com.moyamoya.domain.word.repository.WordRepository;
import com.moyamoya.global.exception.CustomException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RepetitionServiceTest {
    @Mock private ChildRepository children;
    @Mock private WordRepository words;
    @Mock private LearnedWordRepository cards;
    @Mock private SttService stt;
    @Mock private MoyaTtsService tts;
    @Mock private QuestionDataLoader loader;
    private RepetitionService service;
    private Child child;
    private Word word;
    private final MockMultipartFile audio = new MockMultipartFile("audio", "voice.wav", "audio/wav", new byte[]{1});

    @BeforeEach
    void setUp() {
        service = new RepetitionService(children, words, stt,
                new RepetitionEvaluationService(new PronunciationSimilarity(), loader, words),
                new WordCardAcquisitionService(children, words, cards), new RepetitionDialogueService(tts));
    }

    private void ownedWord() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        child = Child.create(user, "지우", AgeGroup.AGE_5);
        word = mock(Word.class);
        when(children.findById(2L)).thenReturn(Optional.of(child));
        when(words.findById(1L)).thenReturn(Optional.of(word));
    }

    private void allowAcquisition() {
        when(children.findByIdForUpdate(2L)).thenReturn(Optional.of(child));
        when(word.getWord()).thenReturn("적금");
    }

    @ParameterizedTest
    @CsvSource({"적금,true", "사과,false", "NO_SPEECH,false"})
    void recognizedResultCreatesCardAndOnlySuccessEarnsSticker(String spoken, boolean correct) {
        ownedWord();
        allowAcquisition();
        if (spoken.equals("NO_SPEECH")) {
            when(stt.transcribe(audio)).thenThrow(new CustomException(QuestionErrorCode.STT_NO_SPEECH));
        } else {
            when(stt.transcribe(audio)).thenReturn(spoken);
        }
        when(cards.save(any())).thenAnswer(invocation -> {
            LearnedWord card = invocation.getArgument(0);
            assertThat(card.isNew()).isTrue();
            assertThat(card.isStickerEarned()).isEqualTo(correct);
            ReflectionTestUtils.setField(card, "id", 35L);
            return card;
        });

        var result = service.repeat(7L, 2L, 1L, audio);

        assertThat(result.wordId()).isEqualTo(1L);
        assertThat(result.learnedWordId()).isEqualTo(35L);
        assertThat(result.isCorrect()).isEqualTo(correct);
        assertThat(result.isNewAcquisition()).isTrue();
        assertThat(result.repetitionDialogue().text()).isEqualTo(correct
                ? "우와! 적금! 잘했어!" : "적금! 적금! 다음에 또 해보자!");
        assertThat(result.acquisitionDialogue().text()).isEqualTo("새로운 단어 카드를 받았어! 수첩에서 확인해 봐!");
        var order = inOrder(stt, children, cards);
        order.verify(stt).transcribe(audio);
        order.verify(children).findByIdForUpdate(2L);
        order.verify(cards).save(any());
    }

    @Test
    void relearningGrantsStickerWithoutNewCardOrResettingViewedState() {
        ownedWord();
        allowAcquisition();
        when(stt.transcribe(audio)).thenReturn("적금");
        LearnedWord card = LearnedWord.create(child, word, false);
        ReflectionTestUtils.setField(card, "id", 35L);
        card.markAsViewed();
        when(cards.findByChild_IdAndWord_Id(2L, 1L)).thenReturn(Optional.of(card));

        var result = service.repeat(7L, 2L, 1L, audio);

        assertThat(result.isCorrect()).isTrue();
        assertThat(result.isNewAcquisition()).isFalse();
        assertThat(card.isNew()).isFalse();
        assertThat(card.isStickerEarned()).isTrue();
        verify(cards, never()).save(any());
    }

    private void error(String code, Long userId, Long childId, Long wordId) {
        assertThatThrownBy(() -> service.repeat(userId, childId, wordId, audio))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo(code));
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL|2|1|AUTH401", "0|2|1|AUTH401", "7|NULL|1|CHILD400", "7|0|1|CHILD400",
            "7|2|NULL|WORD400", "7|2|-1|WORD400"}, delimiter = '|', nullValues = "NULL")
    void invalidIdsDoNotCallSttOrRepositories(Long userId, Long childId, Long wordId, String code) {
        error(code, userId, childId, wordId);
        verifyNoInteractions(children, words, stt, cards);
    }

    @Test
    void missingChildDoesNotCallStt() {
        error("CHILD404", 7L, 2L, 1L);
        verifyNoInteractions(words, stt, cards);
    }

    @Test
    void anotherParentsChildDoesNotCallStt() {
        User other = mock(User.class);
        when(other.getId()).thenReturn(8L);
        when(children.findById(2L)).thenReturn(Optional.of(Child.create(other, "서아", AgeGroup.AGE_5)));
        error("CHILD403", 7L, 2L, 1L);
        verifyNoInteractions(words, stt, cards);
    }

    @Test
    void missingWordDoesNotCallStt() {
        ownedWord();
        when(words.findById(1L)).thenReturn(Optional.empty());
        error("WORD404", 7L, 2L, 1L);
        verifyNoInteractions(stt, cards);
    }

    @ParameterizedTest
    @CsvSource({"INVALID_AUDIO_FILE,REPETITION400", "STT_RECOGNITION_FAILED,REPETITION_STT_FAILED"})
    void audioAndSttErrorsAreTranslatedWithoutSaving(QuestionErrorCode cause, String code) {
        ownedWord();
        when(stt.transcribe(audio)).thenThrow(new CustomException(cause));
        error(code, 7L, 2L, 1L);
        verifyNoInteractions(loader, cards);
        verify(children, never()).findByIdForUpdate(any());
    }

    @Test
    void unknownErrorIsNotTreatedAsNoSpeech() {
        ownedWord();
        when(stt.transcribe(audio)).thenThrow(new CustomException(WordErrorCode.WORD404));
        error("WORD404", 7L, 2L, 1L);
        verifyNoInteractions(cards);
    }

    @Test
    void ownershipIsCheckedAgainAfterSttBeforeSaving() {
        ownedWord();
        when(word.getWord()).thenReturn("적금");
        when(stt.transcribe(audio)).thenReturn("적금");
        User other = mock(User.class);
        when(other.getId()).thenReturn(8L);
        when(children.findByIdForUpdate(2L)).thenReturn(Optional.of(Child.create(other, "서아", AgeGroup.AGE_5)));
        error("CHILD403", 7L, 2L, 1L);
        verifyNoInteractions(cards);
        verifyNoInteractions(tts);
    }

    @Test
    void ttsFailureAfterAcquisitionKeepsCardAndReturnsBothTexts() {
        ownedWord();
        allowAcquisition();
        when(stt.transcribe(audio)).thenReturn("적금");
        when(tts.synthesize(anyString())).thenThrow(new IllegalStateException("TTS unavailable"));
        LearnedWord card = LearnedWord.create(child, word, false);
        ReflectionTestUtils.setField(card, "id", 35L);
        card.markAsViewed();
        when(cards.findByChild_IdAndWord_Id(2L, 1L)).thenReturn(Optional.of(card));

        var result = service.repeat(7L, 2L, 1L, audio);

        assertThat(result.isCorrect()).isTrue();
        assertThat(result.learnedWordId()).isEqualTo(35L);
        assertThat(card.isStickerEarned()).isTrue();
        assertThat(card.isNew()).isFalse();
        assertThat(result.repetitionDialogue().text()).isEqualTo("우와! 적금! 잘했어!");
        assertThat(result.acquisitionDialogue().text()).isEqualTo("이미 수첩에 있는 단어야!");
        assertThat(result.repetitionDialogue().audioBase64()).isNull();
        assertThat(result.acquisitionDialogue().audioBase64()).isNull();
        var order = inOrder(children, tts);
        order.verify(children).findByIdForUpdate(2L);
        order.verify(tts).synthesize("우와! 적금! 잘했어!");
        order.verify(tts).synthesize("이미 수첩에 있는 단어야!");
    }
}

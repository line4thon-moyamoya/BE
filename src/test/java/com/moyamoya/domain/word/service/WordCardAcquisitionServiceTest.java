package com.moyamoya.domain.word.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.word.entity.LearnedWord;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.repository.LearnedWordRepository;
import com.moyamoya.domain.word.repository.WordRepository;
import com.moyamoya.global.exception.CustomException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WordCardAcquisitionServiceTest {
    @Mock private ChildRepository children;
    @Mock private WordRepository words;
    @Mock private LearnedWordRepository cards;
    private WordCardAcquisitionService service;
    private Child child;
    private Word word;

    @BeforeEach
    void setUp() {
        service = new WordCardAcquisitionService(children, words, cards);
    }

    private void ownedWord() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(7L);
        child = Child.create(owner, "지우", AgeGroup.AGE_5);
        word = mock(Word.class);
        when(children.findByIdForUpdate(2L)).thenReturn(Optional.of(child));
        when(words.findById(1L)).thenReturn(Optional.of(word));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void createsNewCardFromServerResultWithNewBadge(boolean correct) {
        ownedWord();
        when(cards.findByChild_IdAndWord_Id(2L, 1L)).thenReturn(Optional.empty());
        when(cards.save(any(LearnedWord.class))).thenAnswer(invocation -> {
            LearnedWord card = invocation.getArgument(0);
            assertThat(card.getChild()).isSameAs(child);
            assertThat(card.getWord()).isSameAs(word);
            assertThat(card.isNew()).isTrue();
            assertThat(card.isStickerEarned()).isEqualTo(correct);
            ReflectionTestUtils.setField(card, "id", 35L);
            return card;
        });

        var result = service.acquireCard(7L, 2L, 1L, correct);

        assertThat(result.learnedWordId()).isEqualTo(35L);
        assertThat(result.isNewAcquisition()).isTrue();
        assertThat(result.stickerEarned()).isEqualTo(correct);
        var order = inOrder(children, words, cards);
        order.verify(children).findByIdForUpdate(2L);
        order.verify(words).findById(1L);
        order.verify(cards).findByChild_IdAndWord_Id(2L, 1L);
        order.verify(cards).save(any(LearnedWord.class));
    }

    @ParameterizedTest
    @CsvSource({"false,false,false", "false,false,true", "false,true,false", "false,true,true",
            "true,false,false", "true,false,true", "true,true,false", "true,true,true"})
    void relearningPreservesNewAndNeverRemovesEarnedSticker(boolean earned, boolean correct, boolean isNew) {
        ownedWord();
        LearnedWord card = LearnedWord.create(child, word, earned);
        ReflectionTestUtils.setField(card, "id", 35L);
        if (!isNew) {
            card.markAsViewed();
        }
        when(cards.findByChild_IdAndWord_Id(2L, 1L)).thenReturn(Optional.of(card));

        var result = service.acquireCard(7L, 2L, 1L, correct);

        assertThat(result.learnedWordId()).isEqualTo(35L);
        assertThat(result.isNewAcquisition()).isFalse();
        assertThat(result.stickerEarned()).isEqualTo(earned || correct);
        assertThat(card.isStickerEarned()).isEqualTo(earned || correct);
        assertThat(card.isNew()).isEqualTo(isNew);
        verify(cards, never()).save(any());
    }

    @Test
    void repeatedSuccessDoesNotCreateAnotherCardOrResetViewedState() {
        ownedWord();
        LearnedWord card = LearnedWord.create(child, word, false);
        card.markAsViewed();
        when(cards.findByChild_IdAndWord_Id(2L, 1L)).thenReturn(Optional.of(card));
        service.acquireCard(7L, 2L, 1L, true);
        service.acquireCard(7L, 2L, 1L, true);
        service.acquireCard(7L, 2L, 1L, false);
        assertThat(card.isStickerEarned()).isTrue();
        assertThat(card.isNew()).isFalse();
        verify(cards, never()).save(any());
    }

    private void error(String code, Long userId, Long childId, Long wordId) {
        assertThatThrownBy(() -> service.acquireCard(userId, childId, wordId, false))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo(code));
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL|2|1|AUTH401", "0|2|1|AUTH401", "7|NULL|1|CHILD400", "7|-1|1|CHILD400",
            "7|2|NULL|WORD400", "7|2|0|WORD400"}, delimiter = '|', nullValues = "NULL")
    void rejectsInvalidIdsBeforeDatabaseAccess(Long user, Long childId, Long wordId, String code) {
        error(code, user, childId, wordId);
        verifyNoInteractions(children, words, cards);
    }

    @Test
    void rejectsMissingChild() {
        when(children.findByIdForUpdate(2L)).thenReturn(Optional.empty());
        error("CHILD404", 7L, 2L, 1L);
        verifyNoInteractions(words, cards);
    }

    @Test
    void rejectsOtherParentsChildBeforeWordLookup() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(8L);
        when(children.findByIdForUpdate(2L)).thenReturn(Optional.of(Child.create(owner, "서아", AgeGroup.AGE_5)));
        error("CHILD403", 7L, 2L, 1L);
        verifyNoInteractions(words, cards);
    }

    @Test
    void rejectsMissingWordWithoutSavingCard() {
        ownedWord();
        when(words.findById(1L)).thenReturn(Optional.empty());
        error("WORD404", 7L, 2L, 1L);
        verifyNoInteractions(cards);
    }
}

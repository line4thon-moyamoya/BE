package com.moyamoya.domain.wordbook.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.word.entity.LearnedWord;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.repository.LearnedWordRepository;
import com.moyamoya.global.exception.CustomException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WordbookServiceTest {
    @Test
    void marksNewCardViewedAndPreservesSticker() {
        Child child = ownedChild();
        LearnedWord card = LearnedWord.create(child, mock(Word.class), true);
        when(cards.findByIdAndChild_Id(35L, 2L)).thenReturn(Optional.of(card));
        service.markAsViewed(7L, 2L, 35L);
        assertThat(card.isNew()).isFalse();
        assertThat(card.isStickerEarned()).isTrue();
    }

    @Test
    void repeatedViewingSucceeds() {
        Child child = ownedChild();
        LearnedWord card = LearnedWord.create(child, mock(Word.class), false);
        when(cards.findByIdAndChild_Id(35L, 2L)).thenReturn(Optional.of(card));
        service.markAsViewed(7L, 2L, 35L);
        service.markAsViewed(7L, 2L, 35L);
        assertThat(card.isNew()).isFalse();
        assertThat(card.isStickerEarned()).isFalse();
    }

    @Test
    void viewingMissingCardReturnsCard404() {
        ownedChild();
        when(cards.findByIdAndChild_Id(35L, 2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.markAsViewed(7L, 2L, 35L))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode().getCode()).isEqualTo("LEARNED_WORD404"));
    }

    @Test
    void viewingMissingChildDoesNotQueryCard() {
        when(children.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.markAsViewed(7L, 2L, 35L))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode().getCode()).isEqualTo("CHILD404"));
        verifyNoInteractions(cards);
    }

    @Test
    void viewingOtherParentsChildDoesNotQueryCard() {
        ownedChild();
        assertThatThrownBy(() -> service.markAsViewed(8L, 2L, 35L))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode().getCode()).isEqualTo("CHILD403"));
        verifyNoInteractions(cards);
    }
    private final ChildRepository children = mock(ChildRepository.class);
    private final LearnedWordRepository cards = mock(LearnedWordRepository.class);
    private final WordbookService service = new WordbookService(children, cards);

    private Child ownedChild() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(7L);
        Child child = Child.create(owner, "지우", AgeGroup.AGE_5);
        when(children.findById(2L)).thenReturn(Optional.of(child));
        return child;
    }

    @Test
    void mapsCardsAndPreservesNewAndStickerStates() {
        Child child = ownedChild();
        Word word = mock(Word.class);
        when(word.getId()).thenReturn(10L);
        when(word.getWord()).thenReturn("자동차");
        when(word.getImageUrl()).thenReturn("/images/words/car.png");
        LearnedWord fresh = LearnedWord.create(child, word, true);
        LearnedWord viewed = LearnedWord.create(child, word, false);
        viewed.markAsViewed();
        when(cards.findAllWithWordByChildId(2L)).thenReturn(List.of(fresh, viewed));

        var result = service.getWordbook(7L, 2L);

        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.learnedWords().getFirst().wordId()).isEqualTo(10L);
        assertThat(result.learnedWords().getFirst().word()).isEqualTo("자동차");
        assertThat(result.learnedWords().getFirst().imageUrl()).isEqualTo("/images/words/car.png");
        assertThat(result.learnedWords().getFirst().stickerEarned()).isTrue();
        assertThat(result.learnedWords().getFirst().isNew()).isTrue();
        assertThat(result.learnedWords().getLast().stickerEarned()).isFalse();
        assertThat(result.learnedWords().getLast().isNew()).isFalse();
        assertThat(fresh.isNew()).isTrue();
        verify(cards, never()).save(any());
    }

    @Test
    void returnsEmptyWordbook() {
        ownedChild();
        when(cards.findAllWithWordByChildId(2L)).thenReturn(List.of());
        var result = service.getWordbook(7L, 2L);
        assertThat(result.totalCount()).isZero();
        assertThat(result.learnedWords()).isEmpty();
    }

    @Test
    void preservesNullImage() {
        Child child = ownedChild();
        when(cards.findAllWithWordByChildId(2L))
                .thenReturn(List.of(LearnedWord.create(child, mock(Word.class), false)));
        assertThat(service.getWordbook(7L, 2L).learnedWords().getFirst().imageUrl()).isNull();
    }

    @Test
    void rejectsMissingChildBeforeQueryingCards() {
        when(children.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getWordbook(7L, 2L))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode().getCode()).isEqualTo("CHILD404"));
        verifyNoInteractions(cards);
    }

    @Test
    void rejectsOtherParentsChildBeforeQueryingCards() {
        ownedChild();
        assertThatThrownBy(() -> service.getWordbook(8L, 2L))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode().getCode()).isEqualTo("CHILD403"));
        verifyNoInteractions(cards);
    }
}

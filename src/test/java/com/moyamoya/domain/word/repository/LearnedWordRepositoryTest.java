package com.moyamoya.domain.word.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.word.entity.LearnedWord;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.wordbook.dto.response.LearnedWordResponse;
import com.moyamoya.domain.wordbook.service.WordbookService;
import com.moyamoya.global.exception.CustomException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LearnedWordRepositoryTest {
    @Mock private ChildRepository childRepository;
    @Mock private LearnedWordRepository repository;
    private WordbookService service;
    private Child child;

    @BeforeEach
    void setUp() {
        service = new WordbookService(childRepository, repository);
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        child = Child.create(user, "지우", AgeGroup.AGE_5);
        when(childRepository.findById(2L)).thenReturn(Optional.of(child));
    }

    @Test
    void viewingUsesChildScopedLookupAndPreservesOtherCardAndSticker() {
        Word word = mock(Word.class);
        LearnedWord card = LearnedWord.create(child, word, true);
        LearnedWord untouched = LearnedWord.create(child, word, false);
        when(repository.findByIdAndChild_Id(35L, 2L)).thenReturn(Optional.of(card));
        when(repository.findAllWithWordByChildId(2L)).thenReturn(List.of(card, untouched));

        service.markAsViewed(7L, 2L, 35L);
        service.markAsViewed(7L, 2L, 35L);
        var response = service.getWordbook(7L, 2L);

        verify(repository, times(2)).findByIdAndChild_Id(35L, 2L);
        assertThat(response.learnedWords()).extracting(LearnedWordResponse::isNew).containsExactly(false, true);
        assertThat(card.isStickerEarned()).isTrue();
        assertThat(untouched.isStickerEarned()).isFalse();
    }

    @Test
    void emptyChildScopedLookupReturnsCardNotFound() {
        when(repository.findByIdAndChild_Id(35L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsViewed(7L, 2L, 35L))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode().getCode()).isEqualTo("LEARNED_WORD404"));

        verify(repository).findByIdAndChild_Id(35L, 2L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void mapsCardsInRepositoryOrderWithoutChangingNewFlags() {
        LearnedWord first = mock(LearnedWord.class);
        LearnedWord second = mock(LearnedWord.class);
        Word firstWord = mock(Word.class);
        Word secondWord = mock(Word.class);
        when(first.getId()).thenReturn(35L);
        when(second.getId()).thenReturn(34L);
        when(first.getWord()).thenReturn(firstWord);
        when(second.getWord()).thenReturn(secondWord);
        when(firstWord.getWord()).thenReturn("적금");
        when(secondWord.getWord()).thenReturn("자동차");
        when(repository.findAllWithWordByChildId(2L)).thenReturn(List.of(first, second));

        var response = service.getWordbook(7L, 2L);

        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.learnedWords()).extracting(LearnedWordResponse::learnedWordId)
                .containsExactly(35L, 34L);
        assertThat(response.learnedWords()).extracting(LearnedWordResponse::word).containsExactly("적금", "자동차");
        verify(repository).findAllWithWordByChildId(2L);
        verify(first, never()).markAsViewed();
        verify(second, never()).markAsViewed();
    }

    @Test
    void returnsEmptyWordbookWhenRepositoryReturnsNoCards() {
        when(repository.findAllWithWordByChildId(2L)).thenReturn(List.of());

        var response = service.getWordbook(7L, 2L);

        assertThat(response.totalCount()).isZero();
        assertThat(response.learnedWords()).isEmpty();
        verify(repository).findAllWithWordByChildId(2L);
    }
}

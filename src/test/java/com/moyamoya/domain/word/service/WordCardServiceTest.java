package com.moyamoya.domain.word.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.question.service.CandidateTtsService;
import jakarta.persistence.EntityManager;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.word.entity.LearnedWord;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.repository.LearnedWordRepository;
import com.moyamoya.domain.word.repository.WordRepository;
import com.moyamoya.global.exception.CustomException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class WordCardServiceTest {
    private final ChildRepository children = mock(ChildRepository.class);
    private final LearnedWordRepository cards = mock(LearnedWordRepository.class);
    private final CandidateTtsService tts = mock(CandidateTtsService.class);
    private final WordCardService service = new WordCardService(new WordLookupService(children,
            mock(WordRepository.class), cards),
            new WordDescriptionAudioService(tts, mock(EntityManager.class)));

    private Child child(AgeGroup age) {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(7L);
        Child child = Child.create(owner, "지우", age);
        when(children.findById(2L)).thenReturn(Optional.of(child));
        return child;
    }

    @ParameterizedTest
    @EnumSource(AgeGroup.class)
    void selectsAgeSpecificFieldsWithoutChangingNewOrSticker(AgeGroup age) {
        Child child = child(age);
        Word word = Word.createExternal("적금", "기본 뜻", null, "어린 설명", "다섯 살 설명", null,
                null, "노래", null, "image.png", null, "young.wav", "five.wav");
        LearnedWord card = LearnedWord.create(child, word, true);
        when(tts.resolveAudioUrl(anyString())).thenAnswer(invocation -> "https://audio.test/" + invocation.getArgument(0));
        when(cards.findByIdAndChild_Id(35L, 2L)).thenReturn(Optional.of(card));

        var result = service.getWordCardDetail(7L, 2L, 35L);

        assertThat(result.word()).isEqualTo("적금");
        assertThat(result.description()).isEqualTo(age == AgeGroup.AGE_3_4 ? "어린 설명" : "다섯 살 설명");
        assertThat(result.descriptionAudioUrl()).isEqualTo("https://audio.test/" + (age == AgeGroup.AGE_3_4 ? "young.wav" : "five.wav"));
        verify(tts, never()).ensureStoredAudio(any(), any());
        assertThat(result.imageUrl()).isEqualTo("image.png");
        assertThat(card.isNew()).isTrue();
        assertThat(card.isStickerEarned()).isTrue();
        verify(cards, never()).save(any());
    }

    @Test
    void preservesNullFieldsAndViewedState() {
        Child child = child(AgeGroup.AGE_5);
        LearnedWord card = LearnedWord.create(child, mock(Word.class), false);
        card.markAsViewed();
        when(cards.findByIdAndChild_Id(35L, 2L)).thenReturn(Optional.of(card));
        var result = service.getWordCardDetail(7L, 2L, 35L);
        assertThat(result.description()).isNull();
        assertThat(result.imageUrl()).isNull();
        assertThat(result.descriptionAudioUrl()).isNull();
        assertThat(card.isNew()).isFalse();
        assertThat(card.isStickerEarned()).isFalse();
    }

    private void error(String code, Long userId, Long childId, Long cardId) {
        assertThatThrownBy(() -> service.getWordCardDetail(userId, childId, cardId))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo(code));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidChildId(Long id) {
        error("CHILD400", 7L, id, 35L);
        verifyNoInteractions(children, cards);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidCardId(Long id) {
        error("LEARNED_WORD400", 7L, 2L, id);
        verifyNoInteractions(children, cards);
    }

    @Test
    void rejectsMissingChildBeforeCardLookup() {
        when(children.findById(2L)).thenReturn(Optional.empty());
        error("CHILD404", 7L, 2L, 35L);
        verifyNoInteractions(cards);
    }

    @Test
    void rejectsOtherParentsChildBeforeCardLookup() {
        child(AgeGroup.AGE_5);
        error("CHILD403", 8L, 2L, 35L);
        verifyNoInteractions(cards);
    }

    @Test
    void rejectsCardMissingFromRequestedChild() {
        child(AgeGroup.AGE_5);
        when(cards.findByIdAndChild_Id(35L, 2L)).thenReturn(Optional.empty());
        error("LEARNED_WORD404", 7L, 2L, 35L);
        verify(cards).findByIdAndChild_Id(35L, 2L);
    }

    @Test
    void rejectsMissingLinkedWord() {
        child(AgeGroup.AGE_5);
        when(cards.findByIdAndChild_Id(35L, 2L)).thenReturn(Optional.of(mock(LearnedWord.class)));
        error("WORD404", 7L, 2L, 35L);
    }
}

package com.moyamoya.domain.word.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.repository.WordRepository;
import com.moyamoya.global.exception.CustomException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class WordServiceTest {
    private final ChildRepository children = mock(ChildRepository.class);
    private final WordRepository words = mock(WordRepository.class);
    private final WordService service = new WordService(children, words);

    private void child(AgeGroup age) {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(7L);
        when(children.findById(2L)).thenReturn(Optional.of(Child.create(owner, "지우", age)));
    }

    @ParameterizedTest
    @EnumSource(AgeGroup.class)
    void selectsDescriptionAndAudioForChildAge(AgeGroup age) {
        child(age);
        Word word = Word.createExternal("적금", "기본 뜻", null, "어린 설명", "다섯 살 설명",
                null, null, "노래", null, null, null, "young.mp3", "five.mp3");
        when(words.findById(1L)).thenReturn(Optional.of(word));
        var response = service.getWordDescription(7L, 1L, 2L);
        assertThat(response.word()).isEqualTo("적금");
        assertThat(response.description()).isEqualTo(age == AgeGroup.AGE_3_4 ? "어린 설명" : "다섯 살 설명");
        assertThat(response.descriptionAudioUrl()).isEqualTo(age == AgeGroup.AGE_3_4 ? "young.mp3" : "five.mp3");
        verify(words).findById(1L);
        verify(words, never()).save(any());
    }

    @Test
    void returnsNullForUnregisteredDescriptionAndAudio() {
        child(AgeGroup.AGE_5);
        when(words.findById(1L)).thenReturn(Optional.of(mock(Word.class)));
        var response = service.getWordDescription(7L, 1L, 2L);
        assertThat(response.description()).isNull();
        assertThat(response.descriptionAudioUrl()).isNull();
    }

    private void assertError(String code, Long userId, Long wordId, Long childId) {
        assertThatThrownBy(() -> service.getWordDescription(userId, wordId, childId))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode().getCode()).isEqualTo(code));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidWordId(Long id) {
        assertError("WORD400", 7L, id, 2L);
        verifyNoInteractions(children, words);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidChildId(Long id) {
        assertError("CHILD400", 7L, 1L, id);
        verifyNoInteractions(children, words);
    }

    @Test
    void rejectsMissingChild() {
        when(children.findById(2L)).thenReturn(Optional.empty());
        assertError("CHILD404", 7L, 1L, 2L);
        verifyNoInteractions(words);
    }

    @Test
    void rejectsOtherParentsChildBeforeWordLookup() {
        child(AgeGroup.AGE_5);
        assertError("CHILD403", 8L, 1L, 2L);
        verifyNoInteractions(words);
    }

    @Test
    void rejectsMissingWord() {
        child(AgeGroup.AGE_5);
        when(words.findById(1L)).thenReturn(Optional.empty());
        assertError("WORD404", 7L, 1L, 2L);
    }
}

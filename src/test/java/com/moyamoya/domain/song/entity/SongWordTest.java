package com.moyamoya.domain.song.entity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.word.entity.LearnedWord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SongWordTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void keepsCardAndOrderWithoutChangingCardState(int order) {
        Song song = mock(Song.class);
        LearnedWord card = mock(LearnedWord.class);
        SongWord songWord = SongWord.create(song, card, order);
        assertThat(songWord.getSong()).isSameAs(song);
        assertThat(songWord.getLearnedWord()).isSameAs(card);
        assertThat(songWord.getWordOrder()).isEqualTo(order);
        verifyNoInteractions(card);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 6})
    void orderMustBeBetweenOneAndFive(int order) {
        assertThatThrownBy(() -> SongWord.create(mock(Song.class), mock(LearnedWord.class), order))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void songAndCardAreRequired() {
        assertThatThrownBy(() -> SongWord.create(null, mock(LearnedWord.class), 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SongWord.create(mock(Song.class), null, 1))
                .isInstanceOf(NullPointerException.class);
    }
}

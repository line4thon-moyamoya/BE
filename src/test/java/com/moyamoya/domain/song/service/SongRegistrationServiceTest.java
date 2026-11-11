package com.moyamoya.domain.song.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.song.entity.Song;
import com.moyamoya.domain.song.model.SongGenerationRequested;
import org.springframework.context.ApplicationEventPublisher;
import com.moyamoya.domain.song.entity.SongStatus;
import com.moyamoya.domain.song.entity.SongWord;
import com.moyamoya.domain.song.repository.SongRepository;
import com.moyamoya.domain.song.repository.SongWordRepository;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.word.entity.LearnedWord;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.repository.LearnedWordRepository;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SongRegistrationServiceTest {
    @Mock private LearnedWordRepository cards;
    @Mock private SongRepository songs;
    @Mock private SongWordRepository songWords;
    @Mock private ApplicationEventPublisher events;
    private SongRegistrationService service;
    private Child child;

    @BeforeEach
    void setUp() {
        service = new SongRegistrationService(cards, songs, songWords, events);
        child = Child.create(mock(User.class), "지우", AgeGroup.AGE_5);
        ReflectionTestUtils.setField(child, "id", 2L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 2, 3, 4})
    void fewerThanFiveCardsDoesNotRegisterSong(long count) {
        when(cards.countUnusedForSongByChildId(2L)).thenReturn(count);

        var result = service.register(child);

        assertThat(result.couponCount()).isEqualTo(count);
        assertThat(result.songId()).isNull();
        verifyNoInteractions(songs, songWords, events);
        verify(cards, never()).findUnusedForSongByChildId(any(), any());
    }

    @ParameterizedTest
    @ValueSource(longs = {5, 7, 12})
    void reservesExactlyFiveCardsInAcquisitionOrder(long count) {
        var selected = selectedCards();
        when(cards.countUnusedForSongByChildId(2L)).thenReturn(count);
        when(cards.findUnusedForSongByChildId(2L, PageRequest.of(0, 5))).thenReturn(selected);
        when(songs.countByChild_Id(2L)).thenReturn(2L);
        when(songs.save(any())).thenAnswer(invocation -> {
            Song song = invocation.getArgument(0);
            assertThat(song.getChild()).isSameAs(child);
            assertThat(song.getTitle()).isEqualTo("우리 노래 3");
            assertThat(song.getStatus()).isEqualTo(SongStatus.GENERATING);
            assertThat(song.getAudioUrl()).isNull();
            ReflectionTestUtils.setField(song, "id", 12L);
            return song;
        });

        var result = service.register(child);

        assertThat(result.couponCount()).isEqualTo(count - 5);
        assertThat(result.songId()).isEqualTo(12L);
        var order = inOrder(songWords, events);
        order.verify(songWords).saveAll(anyList());
        order.verify(events).publishEvent(new SongGenerationRequested(12L));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SongWord>> captor = ArgumentCaptor.forClass(List.class);
        verify(songWords).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(SongWord::getLearnedWord).containsExactlyElementsOf(selected);
        assertThat(captor.getValue()).extracting(SongWord::getWordOrder).containsExactly(1, 2, 3, 4, 5);
        assertThat(captor.getValue()).allSatisfy(sw -> assertThat(sw.getSong().getId()).isEqualTo(12L));
        assertThat(selected).allSatisfy(card -> {
            assertThat(card.isNew()).isTrue();
            assertThat(card.isStickerEarned()).isFalse();
        });
    }

    @Test
    void missingCardsAbortsInsteadOfSavingIncompleteSong() {
        when(cards.countUnusedForSongByChildId(2L)).thenReturn(5L);
        when(cards.findUnusedForSongByChildId(2L, PageRequest.of(0, 5)))
                .thenReturn(selectedCards().subList(0, 4));

        assertThatThrownBy(() -> service.register(child)).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(songs, songWords, events);
    }

    private List<LearnedWord> selectedCards() {
        return IntStream.range(0, 5).mapToObj(index -> {
            LearnedWord card = LearnedWord.create(child, mock(Word.class), false);
            ReflectionTestUtils.setField(card, "id", (long) index + 1);
            return card;
        }).toList();
    }
}

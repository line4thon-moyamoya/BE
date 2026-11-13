package com.moyamoya.domain.song.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.song.dto.response.SongSummaryResponse;
import com.moyamoya.domain.song.entity.Song;
import com.moyamoya.domain.song.entity.SongStatus;
import com.moyamoya.domain.song.entity.SongWord;
import com.moyamoya.domain.song.repository.SongRepository;
import com.moyamoya.domain.song.repository.SongWordRepository;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.word.entity.LearnedWord;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.s3.S3Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class SongServiceTest {
    private final ChildRepository children = mock(ChildRepository.class);
    private final SongRepository songs = mock(SongRepository.class);
    private final SongWordRepository songWords = mock(SongWordRepository.class);
    private final S3Service audio = mock(S3Service.class);
    private final SongService service = new SongService(children, songs, songWords, audio);

    private Child ownedChild() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(7L);
        Child child = Child.create(owner, "지우", AgeGroup.AGE_5);
        when(children.findById(2L)).thenReturn(Optional.of(child));
        return child;
    }

    @Test
    void returnsSongsInRepositoryOrderAndKeepsEachSongsWordOrder() {
        Child child = ownedChild();
        Song newer = song(child, 12L, "우리 노래 2");
        Song older = song(child, 11L, "우리 노래 1");
        List<String> newerWords = List.of("영수증", "소방차", "용기", "적금", "통장");
        List<String> olderWords = List.of("환기", "충전", "소독", "알림장", "국회의원");
        var rows = new ArrayList<SongWord>();
        rows.addAll(words(child, older, olderWords));
        rows.addAll(words(child, newer, newerWords));
        when(songs.findAllByChild_IdOrderByCreatedAtDescIdDesc(2L)).thenReturn(List.of(newer, older));
        when(songWords.findAllWithWordByChildId(2L)).thenReturn(rows);

        var response = service.getSongs(7L, 2L);

        assertThat(response.songs()).containsExactly(
                new SongSummaryResponse(12L, "우리 노래 2", newerWords),
                new SongSummaryResponse(11L, "우리 노래 1", olderWords));
        var order = inOrder(children, songs, songWords);
        order.verify(children).findById(2L);
        order.verify(songs).findAllByChild_IdOrderByCreatedAtDescIdDesc(2L);
        order.verify(songWords).findAllWithWordByChildId(2L);
        verifyNoMoreInteractions(songs, songWords);
    }

    @Test
    void noSongsReturnsEmptyListWithoutQueryingWords() {
        ownedChild();

        assertThat(service.getSongs(7L, 2L).songs()).isEmpty();

        verifyNoInteractions(songWords);
    }

    @ParameterizedTest
    @EnumSource(SongStatus.class)
    void listsRegisteredSongsRegardlessOfGenerationStatus(SongStatus status) {
        Child child = ownedChild();
        Song song = song(child, 12L, "우리 노래 1");
        if (status == SongStatus.READY) {
            song.markAsReady("songs/12.wav");
        } else if (status == SongStatus.FAILED) {
            song.markAsFailed();
        }
        List<String> names = List.of("영수증", "소방차", "용기", "적금", "통장");
        var rows = words(child, song, names);
        when(songs.findAllByChild_IdOrderByCreatedAtDescIdDesc(2L)).thenReturn(List.of(song));
        when(songWords.findAllWithWordByChildId(2L)).thenReturn(rows);

        var response = service.getSongs(7L, 2L);

        assertThat(response.songs()).containsExactly(new SongSummaryResponse(12L, "우리 노래 1", names));
        assertThat(song.getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void invalidChildIdDoesNotQueryRepositories(Long childId) {
        error("CHILD400", 7L, childId);
        verifyNoInteractions(children, songs, songWords);
    }

    @Test
    void missingChildReturns404BeforeSongLookup() {
        error("CHILD404", 7L, 2L);
        verifyNoInteractions(songs, songWords);
    }

    @Test
    void otherParentsChildReturns403BeforeSongLookup() {
        ownedChild();
        error("CHILD403", 8L, 2L);
        verifyNoInteractions(songs, songWords);
    }

    private void error(String code, Long userId, Long childId) {
        assertThatThrownBy(() -> service.getSongs(userId, childId))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo(code));
    }

    private Song song(Child child, Long id, String title) {
        Song song = Song.create(child, title);
        ReflectionTestUtils.setField(song, "id", id);
        return song;
    }

    private List<SongWord> words(Child child, Song song, List<String> names) {
        var result = new ArrayList<SongWord>();
        for (int index = 0; index < names.size(); index++) {
            Word word = mock(Word.class);
            when(word.getWord()).thenReturn(names.get(index));
            result.add(SongWord.create(song, LearnedWord.create(child, word, false), index + 1));
        }
        return result;
    }
}

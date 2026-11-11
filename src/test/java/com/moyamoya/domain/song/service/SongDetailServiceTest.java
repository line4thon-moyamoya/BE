package com.moyamoya.domain.song.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class SongDetailServiceTest {
    private final ChildRepository children = mock(ChildRepository.class);
    private final SongRepository songs = mock(SongRepository.class);
    private final SongWordRepository songWords = mock(SongWordRepository.class);
    private final S3Service audio = mock(S3Service.class);
    private final SongService service = new SongService(children, songs, songWords, audio);

    private Child ownedChild(AgeGroup ageGroup) {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(7L);
        Child child = Child.create(owner, "지우", ageGroup);
        when(children.findById(2L)).thenReturn(Optional.of(child));
        return child;
    }

    private Song registeredSong(Child child) {
        Song song = Song.create(child, "우리 노래 1");
        ReflectionTestUtils.setField(song, "id", 12L);
        when(songs.findByIdAndChild_Id(12L, 2L)).thenReturn(Optional.of(song));
        return song;
    }

    @ParameterizedTest
    @CsvSource({"AGE_3_4,어린 설명", "AGE_5,다섯 살 설명"})
    void readyReturnsOrderedWordsAgeDescriptionAndPresignedAudio(AgeGroup ageGroup, String description) {
        Child child = ownedChild(ageGroup);
        Song song = registeredSong(child);
        song.markAsReady("local/songs/12.wav");
        var rows = new ArrayList<SongWord>();
        List<String> names = List.of("영수증", "소방차", "용기", "적금", "통장");
        for (int index = 0; index < names.size(); index++) {
            Word word = word(names.get(index), "어린 설명", "다섯 살 설명", index % 2 == 0 ? null : "image.png");
            ReflectionTestUtils.setField(word, "id", (long) index + 1);
            rows.add(SongWord.create(song, LearnedWord.create(child, word, true), index + 1));
        }
        when(songWords.findAllBySong_IdOrderByWordOrderAsc(12L)).thenReturn(rows);
        when(audio.getPresignedUrl("local/songs/12.wav")).thenReturn("https://audio.test/12.wav?signed=1");

        var response = service.getSongDetail(7L, 2L, 12L);

        assertThat(response.songId()).isEqualTo(12L);
        assertThat(response.title()).isEqualTo("우리 노래 1");
        assertThat(response.status()).isEqualTo(SongStatus.READY);
        assertThat(response.audioUrl()).isEqualTo("https://audio.test/12.wav?signed=1");
        assertThat(response.words()).extracting("word").containsExactlyElementsOf(names);
        assertThat(response.words()).extracting("wordId").containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(response.words()).extracting("description").containsOnly(description);
        assertThat(response.words()).extracting("imageUrl").containsExactly(null, "image.png", null, "image.png", null);
        assertThat(song.getStatus()).isEqualTo(SongStatus.READY);
        verify(audio).getPresignedUrl("local/songs/12.wav");
        verifyNoMoreInteractions(audio);
        verify(songs, never()).save(any());
        verify(songWords, never()).saveAll(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingAgeDescriptionFallsBackToDefinition(String missing) {
        Child child = ownedChild(AgeGroup.AGE_5);
        Song song = registeredSong(child);
        song.markAsReady("local/songs/12.wav");
        Word word = word("적금", "어린 설명", missing, null);
        when(songWords.findAllBySong_IdOrderByWordOrderAsc(12L))
                .thenReturn(List.of(SongWord.create(song, LearnedWord.create(child, word, false), 1)));

        assertThat(service.getSongDetail(7L, 2L, 12L).words().getFirst().description()).isEqualTo("기본 정의");
    }

    @ParameterizedTest
    @EnumSource(value = SongStatus.class, names = {"GENERATING", "FAILED"})
    void unfinishedSongReturnsStatusWithoutWordOrAudioLookup(SongStatus status) {
        Child child = ownedChild(AgeGroup.AGE_5);
        Song song = registeredSong(child);
        if (status == SongStatus.FAILED) {
            song.markAsFailed();
        }

        var response = service.getSongDetail(7L, 2L, 12L);

        assertThat(response.songId()).isEqualTo(12L);
        assertThat(response.title()).isEqualTo("우리 노래 1");
        assertThat(response.status()).isEqualTo(status);
        assertThat(response.audioUrl()).isNull();
        assertThat(response.words()).isEmpty();
        verifyNoInteractions(songWords, audio);
        verify(songs, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL|12|CHILD400", "0|12|CHILD400", "-1|12|CHILD400",
            "2|NULL|SONG400", "2|0|SONG400", "2|-1|SONG400"}, delimiter = '|', nullValues = "NULL")
    void invalidIdsDoNotAccessRepositories(Long childId, Long songId, String code) {
        error(code, 7L, childId, songId);
        verifyNoInteractions(children, songs, songWords, audio);
    }

    @Test
    void missingChildStopsBeforeSongLookup() {
        error("CHILD404", 7L, 2L, 12L);
        verifyNoInteractions(songs, songWords, audio);
    }

    @Test
    void anotherParentsChildStopsBeforeSongLookup() {
        ownedChild(AgeGroup.AGE_5);
        error("CHILD403", 8L, 2L, 12L);
        verifyNoInteractions(songs, songWords, audio);
    }

    @Test
    void missingOrOtherChildsSongReturnsSong404() {
        ownedChild(AgeGroup.AGE_5);
        error("SONG404", 7L, 2L, 12L);
        verify(songs).findByIdAndChild_Id(12L, 2L);
        verify(songs, never()).findById(any());
        verifyNoInteractions(songWords, audio);
    }

    @Test
    void audioSigningFailureDoesNotChangeSongState() {
        Child child = ownedChild(AgeGroup.AGE_5);
        Song song = registeredSong(child);
        song.markAsReady("local/songs/12.wav");
        when(audio.getPresignedUrl("local/songs/12.wav")).thenThrow(new IllegalStateException("sign failed"));

        assertThatThrownBy(() -> service.getSongDetail(7L, 2L, 12L)).isInstanceOf(IllegalStateException.class);
        assertThat(song.getStatus()).isEqualTo(SongStatus.READY);
        verify(songs, never()).save(any());
    }

    private Word word(String name, String age3To4, String age5, String image) {
        return Word.createExternal(name, "기본 정의", null, age3To4, age5,
                null, null, "사용하지 않는 노래용 설명", null, image, null, null, null);
    }

    private void error(String code, Long userId, Long childId, Long songId) {
        assertThatThrownBy(() -> service.getSongDetail(userId, childId, songId))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo(code));
    }
}

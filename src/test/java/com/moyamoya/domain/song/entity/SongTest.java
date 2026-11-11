package com.moyamoya.domain.song.entity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.child.entity.Child;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SongTest {
    private Song song() {
        return Song.create(mock(Child.class), "우리 노래 1");
    }

    @Test
    void creationStartsGeneratingWithoutAudio() {
        Song song = song();
        assertThat(song.getTitle()).isEqualTo("우리 노래 1");
        assertThat(song.getStatus()).isEqualTo(SongStatus.GENERATING);
        assertThat(song.getAudioUrl()).isNull();
    }

    @Test
    void completedSongKeepsAudioKeyAndCannotBeRetriedOrFailed() {
        Song song = song();
        song.markAsReady("local/songs/1.wav");
        assertThat(song.getStatus()).isEqualTo(SongStatus.READY);
        assertThat(song.getAudioUrl()).isEqualTo("local/songs/1.wav");
        assertThatThrownBy(song::retry).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(song::markAsFailed).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedSongRetriesUsingSameChildAndTitle() {
        Song song = song();
        Child child = song.getChild();
        song.markAsFailed();
        assertThat(song.getStatus()).isEqualTo(SongStatus.FAILED);
        assertThat(song.getAudioUrl()).isNull();
        assertThatThrownBy(() -> song.markAsReady("local/songs/1.wav"))
                .isInstanceOf(IllegalStateException.class);
        song.retry();
        assertThat(song.getStatus()).isEqualTo(SongStatus.GENERATING);
        assertThat(song.getChild()).isSameAs(child);
        assertThat(song.getTitle()).isEqualTo("우리 노래 1");
        song.markAsReady("local/songs/1.wav");
        assertThat(song.getStatus()).isEqualTo(SongStatus.READY);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void missingAudioDoesNotCompleteSong(String key) {
        Song song = song();
        assertThatThrownBy(() -> song.markAsReady(key)).isInstanceOf(IllegalArgumentException.class);
        assertThat(song.getStatus()).isEqualTo(SongStatus.GENERATING);
    }

    @Test
    void generatingSongCannotBeRetried() {
        assertThatThrownBy(() -> song().retry()).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void titleIsRequired(String value) {
        Child child = mock(Child.class);
        assertThatThrownBy(() -> Song.create(child, value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valuesCannotExceedColumnLengths() {
        Child child = mock(Child.class);
        assertThatThrownBy(() -> Song.create(child, "a".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> song().markAsReady("a".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

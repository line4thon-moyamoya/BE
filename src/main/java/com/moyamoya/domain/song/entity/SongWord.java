package com.moyamoya.domain.song.entity;

import com.moyamoya.domain.word.entity.LearnedWord;
import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "song_words", uniqueConstraints = {
        @UniqueConstraint(name = "uk_song_words_learned_word", columnNames = "learned_word_id"),
        @UniqueConstraint(name = "uk_song_words_song_order", columnNames = {"song_id", "word_order"})
})
@Check(constraints = "word_order between 1 and 5")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SongWord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "song_word_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learned_word_id", nullable = false)
    private LearnedWord learnedWord;

    @Column(name = "word_order", nullable = false)
    private int wordOrder;

    public static SongWord create(Song song, LearnedWord learnedWord, int wordOrder) {
        if (wordOrder < 1 || wordOrder > 5) {
            throw new IllegalArgumentException("노래 단어 순서는 1~5여야 합니다.");
        }
        SongWord songWord = new SongWord();
        songWord.song = Objects.requireNonNull(song, "노래 정보가 필요합니다.");
        songWord.learnedWord = Objects.requireNonNull(learnedWord, "학습 카드가 필요합니다.");
        songWord.wordOrder = wordOrder;
        return songWord;
    }

}

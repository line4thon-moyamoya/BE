package com.moyamoya.domain.word.entity;

import com.moyamoya.domain.child.entity.Child;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "learned_words",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_learned_words_child_word",
                        columnNames = {"child_id", "word_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearnedWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "learned_word_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    private Child child;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id", nullable = false)
    private Word word;

    @Column(name = "sticker_earned", nullable = false)
    private boolean stickerEarned;

    @CreationTimestamp
    @Column(name = "learned_at", nullable = false, updatable = false)
    private LocalDateTime learnedAt;

    @Column(name = "is_new", nullable = false)
    private boolean isNew;

    public static LearnedWord create(
            Child child,
            Word word,
            boolean stickerEarned
    ) {
        LearnedWord learnedWord = new LearnedWord();
        learnedWord.child = child;
        learnedWord.word = word;
        learnedWord.stickerEarned = stickerEarned;
        learnedWord.isNew = true;
        return learnedWord;
    }

    public void markAsViewed() {
        this.isNew = false;
    }
}
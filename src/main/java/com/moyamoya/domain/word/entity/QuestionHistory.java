package com.moyamoya.domain.word.entity;

import com.moyamoya.domain.child.entity.Child;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "question_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    private Child child;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id", nullable = false)
    private Word word;

    @Column(name = "recognized_text", length = 255)
    private String recognizedText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static QuestionHistory create(
            Child child,
            Word word,
            String recognizedText
    ) {
        QuestionHistory history = new QuestionHistory();
        history.child = child;
        history.word = word;
        history.recognizedText = recognizedText;
        return history;
    }
}
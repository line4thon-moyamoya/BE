package com.moyamoya.domain.word.entity;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.word.exception.WordErrorCode;
import com.moyamoya.global.exception.CustomException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "word_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WordRequest {

    private static final int CORRECTED_WORD_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "word_request_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    private Child child;

    @Column(name = "recognized_text", nullable = false, length = 255)
    private String recognizedText;

    @Column(name = "corrected_word", length = 100)
    private String correctedWord;

    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WordRequestStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public static WordRequest create(
            Child child,
            String recognizedText,
            String audioUrl
    ) {
        WordRequest request = new WordRequest();
        request.child = child;
        request.recognizedText = recognizedText;
        request.audioUrl = audioUrl;
        request.status = WordRequestStatus.PENDING;
        return request;
    }

    public void resolve(String correctedWord) {
        if (this.status != WordRequestStatus.PENDING) {
            throw new CustomException(WordErrorCode.WORD_REQUEST409);
        }

        String normalizedCorrectedWord = correctedWord == null ? null : correctedWord.trim();
        if (normalizedCorrectedWord == null || normalizedCorrectedWord.isBlank()) {
            throw new CustomException(WordErrorCode.WORD_REQUEST400);
        }
        if (normalizedCorrectedWord.length() > CORRECTED_WORD_MAX_LENGTH) {
            throw new CustomException(WordErrorCode.WORD_REQUEST400);
        }

        this.correctedWord = normalizedCorrectedWord;
        this.status = WordRequestStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
    }

    public void removeAudio() {
        this.audioUrl = null;
    }
}

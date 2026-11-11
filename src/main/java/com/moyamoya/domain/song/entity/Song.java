package com.moyamoya.domain.song.entity;

import com.moyamoya.domain.child.entity.Child;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "songs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Song {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "song_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "child_id", nullable = false)
    private Child child;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "varchar(20)")
    private SongStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static Song create(Child child, String title) {
        requireText(title, 100);
        Song song = new Song();
        song.child = Objects.requireNonNull(child, "아이 정보가 필요합니다.");
        song.title = title;
        song.status = SongStatus.GENERATING;
        return song;
    }

    public void markAsReady(String audioKey) {
        requireStatus(SongStatus.GENERATING);
        requireText(audioKey, 500);
        this.audioUrl = audioKey;
        this.status = SongStatus.READY;
    }

    public void markAsFailed() {
        requireStatus(SongStatus.GENERATING);
        this.audioUrl = null;
        this.status = SongStatus.FAILED;
    }

    public void retry() {
        requireStatus(SongStatus.FAILED);
        this.audioUrl = null;
        this.status = SongStatus.GENERATING;
    }

    private void requireStatus(SongStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("현재 노래 상태에서는 처리할 수 없습니다.");
        }
    }

    private static void requireText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException("노래 정보가 올바르지 않습니다.");
        }
    }
}

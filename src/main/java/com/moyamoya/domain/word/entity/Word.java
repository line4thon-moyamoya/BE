package com.moyamoya.domain.word.entity;

import com.moyamoya.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "words",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_words_word",
                columnNames = "word"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Word extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "word_id")
    private Long id;

    @Column(name = "word", nullable = false, length = 100)
    private String word;

    @Column(name = "definition", nullable = false, columnDefinition = "TEXT")
    private String definition;

    @Column(name = "category", columnDefinition = "TEXT")
    private String category;

    @Column(name = "desc_age_3_4", columnDefinition = "TEXT")
    private String descAge3To4;

    @Column(name = "desc_age_5", columnDefinition = "TEXT")
    private String descAge5;

    @Column(name = "example", columnDefinition = "TEXT")
    private String example;

    @Column(name = "hint", columnDefinition = "TEXT")
    private String hint;

    @Enumerated(EnumType.STRING)
    @Column(name = "heard_from", length = 20)
    private HeardFrom heardFrom;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "word_audio_url", length = 500)
    private String wordAudioUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private WordSourceType sourceType;

    @Column(name = "song_audio_url", length = 500)
    private String songAudioUrl;

    public static Word createExternal(
            String word,
            String definition,
            String category,
            String descAge3To4,
            String descAge5,
            String example,
            String hint,
            HeardFrom heardFrom,
            String imageUrl,
            String wordAudioUrl
    ) {
        Word newWord = new Word();

        newWord.word = word;
        newWord.definition = definition;
        newWord.category = category;
        newWord.descAge3To4 = descAge3To4;
        newWord.descAge5 = descAge5;
        newWord.example = example;
        newWord.hint = hint;
        newWord.heardFrom = heardFrom;
        newWord.imageUrl = imageUrl;
        newWord.wordAudioUrl = wordAudioUrl;

        newWord.sourceType = WordSourceType.EXTERNAL;

        return newWord;
    }

    // CSV 기본 단어 시딩용
    public static Word createCurated(
            String word,
            String definition,
            String category,
            String descAge3To4,
            String descAge5,
            String example,
            String hint,
            HeardFrom heardFrom
    ) {
        Word newWord = new Word();

        newWord.word = word;
        newWord.definition = definition;
        newWord.category = category;
        newWord.descAge3To4 = descAge3To4;
        newWord.descAge5 = descAge5;
        newWord.example = example;
        newWord.hint = hint;
        newWord.heardFrom = heardFrom;

        // 기본 단어
        newWord.sourceType = WordSourceType.CURATED;

        // 음성 및 이미지는 추후 생성
        newWord.imageUrl = null;
        newWord.wordAudioUrl = null;

        return newWord;
    }

    // 단어 발음 음성의 S3 객체 키 저장
    public void updateWordAudioUrl(String s3Key) {
        this.wordAudioUrl = s3Key;
    }

    // 단어 이미지의 S3 객체 키 저장
    public void updateImageUrl(String s3Key) { this.imageUrl = s3Key; }
}

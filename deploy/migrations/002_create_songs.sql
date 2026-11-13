SET NAMES utf8mb4;

CREATE TABLE songs (
    song_id BIGINT NOT NULL AUTO_INCREMENT,
    child_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    audio_url VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (song_id),
    CONSTRAINT fk_songs_child FOREIGN KEY (child_id) REFERENCES children (child_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE song_words (
    song_word_id BIGINT NOT NULL AUTO_INCREMENT,
    song_id BIGINT NOT NULL,
    learned_word_id BIGINT NOT NULL,
    word_order INT NOT NULL,
    PRIMARY KEY (song_word_id),
    UNIQUE KEY uk_song_words_learned_word (learned_word_id),
    UNIQUE KEY uk_song_words_song_order (song_id, word_order),
    CONSTRAINT fk_song_words_song FOREIGN KEY (song_id) REFERENCES songs (song_id),
    CONSTRAINT fk_song_words_learned_word FOREIGN KEY (learned_word_id) REFERENCES learned_words (learned_word_id),
    CONSTRAINT ck_song_words_order CHECK (word_order BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

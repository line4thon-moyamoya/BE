SET NAMES utf8mb4;

ALTER TABLE words
DROP COLUMN description_audio_age_3_4_url,
    DROP COLUMN description_audio_age_5_url,
    DROP COLUMN song_description;

SELECT
    word,
    CONCAT('prod/words/images/', word, '.png') AS image_key
FROM words
WHERE source_type = 'CURATED'
  AND (image_url IS NULL OR image_url = '');

UPDATE words
SET image_url = CONCAT('prod/words/images/', word, '.png')
WHERE source_type = 'CURATED'
  AND (image_url IS NULL OR image_url = '');

SELECT
    word,
    image_url
FROM words
WHERE source_type = 'CURATED'
ORDER BY word;

SELECT
    word
FROM words
WHERE source_type = 'CURATED'
  AND (image_url IS NULL OR image_url = '');
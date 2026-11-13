package com.moyamoya.domain.word.init;

import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.repository.WordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

@Slf4j
@Component
@Profile("image-seed")
@RequiredArgsConstructor
public class WordImageSeeder implements ApplicationRunner {

    private static final String IMAGE_PREFIX = "local/words/images/";

    private final WordRepository wordRepository;
    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.prefix:}")
    private String keyPrefix;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        // local 환경에서만 실행
        if (!"local".equals(keyPrefix)) {
            throw new IllegalStateException(
                    "이미지 시더는 local 환경에서만 실행할 수 있습니다."
            );
        }

        int updated = 0;
        int skipped = 0;

        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(IMAGE_PREFIX)
                .build();

        for (S3Object object : s3Client.listObjectsV2Paginator(request).contents()) {

            String key = object.key();

            // PNG 파일만 처리
            if (!key.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
                skipped++;
                continue;
            }

            String wordName = key
                    .substring(IMAGE_PREFIX.length())
                    .replaceFirst("(?i)\\.png$", "");

            if (wordName.isBlank() || wordName.contains("/")) {
                skipped++;
                continue;
            }

            Word word = wordRepository.findByWord(wordName)
                    .orElse(null);

            if (word == null) {
                log.warn("DB에 없는 단어: {}", wordName);
                skipped++;
                continue;
            }

            // S3 업로드 없이 DB에 객체 키만 저장
            word.updateImageUrl(key);
            updated++;

            log.info("이미지 키 설정: {} → {}", wordName, key);
        }

        log.info(
                "이미지 DB 시딩 완료: {}개, 건너뜀: {}개",
                updated,
                skipped
        );
    }
}
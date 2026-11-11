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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.Resource;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Optional;

@Slf4j
@Component
@Profile("image-seed")
@RequiredArgsConstructor
public class WordImageSeeder implements ApplicationRunner {

    private final WordRepository wordRepository;
    private final S3Client s3Client;
    private final TransactionTemplate transactionTemplate;

    @Value("${AWS_S3_BUCKET}")
    private String bucket;

    @Value("${aws.s3.prefix:dev}")
    private String localPrefix;

    @Override
    public void run(ApplicationArguments args) throws Exception {

        log.info("현재 S3 prefix = [{}]", localPrefix);

        if (!"local".equals(localPrefix)) {
            throw new IllegalStateException(
                    "현재 S3 prefix: " + localPrefix
            );
        }

        Resource[] images =
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:seed/images/*.png");

        int uploaded = 0;
        int skipped = 0;

        for (Resource image : images) {

            String fileName = image.getFilename();

            if (fileName == null) {
                skipped++;
                continue;
            }

            // "1 관리비.png" → "관리비"
            String wordName = fileName
                    .replaceFirst("^\\d+\\s+", "")
                    .replaceFirst("(?i)\\.png$", "")
                    .trim();

            Optional<Word> wordOptional =
                    wordRepository.findByWord(wordName);

            if (wordOptional.isEmpty()) {
                log.warn("DB에 없는 단어: {}", wordName);
                skipped++;
                continue;
            }

            String relativeKey =
                    "words/images/" + wordName + ".png";

            String devKey = "local/" + relativeKey;
            String prodKey = "prod/" + relativeKey;

            byte[] bytes;
            try (var input = image.getInputStream()) {
                bytes = input.readAllBytes();
            }

            // 1. 로컬 S3
            upload(devKey, bytes);

            // 2. 배포 S3
            upload(prodKey, bytes);

            // 3. 로컬 DB에 dev 객체 키 저장
            transactionTemplate.executeWithoutResult(status -> {
                Word word = wordRepository.findByWord(wordName)
                        .orElseThrow();

                word.updateImageUrl(devKey);
                wordRepository.save(word);
            });

            uploaded++;

            log.info(
                    "이미지 완료: {} / dev={} / prod={}",
                    wordName, devKey, prodKey
            );
        }

        log.info("===== 이미지 시딩 완료 =====");
        log.info("업로드 완료: {}개", uploaded);
        log.info("건너뜀: {}개", skipped);
    }

    private void upload(String key, byte[] bytes) {

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("image/png")
                .build();

        s3Client.putObject(
                request,
                RequestBody.fromBytes(bytes)
        );
    }
}

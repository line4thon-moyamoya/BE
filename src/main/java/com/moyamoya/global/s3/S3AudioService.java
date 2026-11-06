package com.moyamoya.global.s3;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class S3AudioService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.prefix:}")
    private String keyPrefix;

    public String uploadWav(String key, byte[] wav) {
        if (key == null || key.isBlank() || wav == null || wav.length == 0) {
            throw new IllegalArgumentException("업로드할 음성 데이터가 올바르지 않습니다.");
        }

        String uploadKey = withPrefix(key);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(uploadKey)
                .contentType("audio/wav")
                .build();

        s3Client.putObject(
                request,
                RequestBody.fromBytes(wav)
        );

        return uploadKey;
    }

    public String getPresignedUrl(String key) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(30))
                        .getObjectRequest(getRequest)
                        .build();

        return s3Presigner.presignGetObject(presignRequest)
                .url()
                .toString();
    }

    private String withPrefix(String key) {
        String normalizedKey = key.startsWith("/") ? key.substring(1) : key;

        if (!StringUtils.hasText(keyPrefix)) {
            return normalizedKey;
        }

        String normalizedPrefix = keyPrefix.trim().replace("\\", "/");
        while (normalizedPrefix.startsWith("/")) {
            normalizedPrefix = normalizedPrefix.substring(1);
        }
        while (normalizedPrefix.endsWith("/")) {
            normalizedPrefix = normalizedPrefix.substring(0, normalizedPrefix.length() - 1);
        }

        if (!StringUtils.hasText(normalizedPrefix)) {
            return normalizedKey;
        }

        String prefixWithSlash = normalizedPrefix + "/";
        if (normalizedKey.startsWith(prefixWithSlash)) {
            return normalizedKey;
        }

        return prefixWithSlash + normalizedKey;
    }
}

package com.moyamoya.domain.song.service;

import com.moyamoya.domain.song.dto.response.SongMixResponse;
import com.moyamoya.global.s3.S3Service;
import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@Service
public class SongAudioStorageService {
    private static final String BACKING_KEY = "songs/backing/moyamoya-v1.m4a";
    private static final int MAX_BACKING_BYTES = 20 * 1024 * 1024;
    private final S3Client s3Client;
    private final S3Service audioService;
    private final String bucket;
    private final String prefix;

    public SongAudioStorageService(S3Client s3Client, S3Service audioService,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.prefix:}") String prefix) {
        this.s3Client = s3Client;
        this.audioService = audioService;
        this.bucket = bucket;
        this.prefix = prefix.trim().replace("\\", "/").replaceAll("^/+|/+$", "");
    }

    public byte[] downloadBacking() {
        String key = prefix.isEmpty() ? BACKING_KEY : prefix + "/" + BACKING_KEY;
        var request = GetObjectRequest.builder().bucket(bucket).key(key).build();
        try (var input = s3Client.getObject(request)) {
            Long size = input.response().contentLength();
            if (size != null && (size <= 0 || size > MAX_BACKING_BYTES)) {
                throw new IllegalStateException("반주는 20MB 이하의 비어 있지 않은 파일이어야 합니다.");
            }
            byte[] backing = input.readNBytes(MAX_BACKING_BYTES + 1);
            if (backing.length == 0 || backing.length > MAX_BACKING_BYTES) {
                throw new IllegalStateException("반주는 20MB 이하의 비어 있지 않은 파일이어야 합니다.");
            }
            return backing;
        } catch (IOException | SdkException exception) {
            throw new IllegalStateException("S3 반주 다운로드에 실패했습니다.", exception);
        }
    }

    public SongMixResponse upload(byte[] audio) {
        String key = audioService.uploadWav("songs/generated/" + UUID.randomUUID() + ".wav", audio);
        return new SongMixResponse(key, audioService.getPresignedUrl(key));
    }
}

package com.moyamoya.domain.song.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moyamoya.global.s3.S3AudioService;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

class SongAudioStorageServiceTest {
    private final S3Client s3 = mock(S3Client.class);
    private final S3AudioService audio = mock(S3AudioService.class);

    private SongAudioStorageService storage() {
        return new SongAudioStorageService(s3, audio, "test-bucket", "local");
    }

    @Test
    void downloadsFixedBackingUnderLocalPrefixAndClosesStream() throws Exception {
        var stream = spy(new ResponseInputStream<>(GetObjectResponse.builder().contentLength(3L).build(),
                new ByteArrayInputStream(new byte[]{1, 2, 3})));
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(stream);
        assertThat(storage().downloadBacking()).containsExactly(1, 2, 3);
        verify(s3).getObject(GetObjectRequest.builder().bucket("test-bucket")
                .key("local/songs/backing/moyamoya-v1.m4a").build());
        verify(stream).close();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"local", "prod", "dev", ""})
    void usesExistingEnvironmentPrefix(String prefix) {
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().contentLength(1L).build(), new ByteArrayInputStream(new byte[]{1})));
        new SongAudioStorageService(s3, audio, "test-bucket", prefix).downloadBacking();
        String key = (prefix.isEmpty() ? "" : prefix + "/") + "songs/backing/moyamoya-v1.m4a";
        verify(s3).getObject(GetObjectRequest.builder().bucket("test-bucket").key(key).build());
    }

    @Test
    void rejectsOversizedBackingBeforeReadingBody() {
        var stream = new ResponseInputStream<>(GetObjectResponse.builder().contentLength(21L * 1024 * 1024).build(),
                new ByteArrayInputStream(new byte[]{1}));
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(stream);
        assertThatIllegalStateException().isThrownBy(() -> storage().downloadBacking());
    }

    @Test
    void rejectsEmptyBacking() {
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().build(), new ByteArrayInputStream(new byte[0])));
        assertThatIllegalStateException().isThrownBy(() -> storage().downloadBacking());
    }

    @Test
    void translatesS3DownloadFailure() {
        when(s3.getObject(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().message("missing").build());
        assertThatIllegalStateException().isThrownBy(() -> storage().downloadBacking())
                .withMessage("S3 반주 다운로드에 실패했습니다.");
    }

    @Test
    void uploadsUniqueRelativeKeyAndPresignsExactReturnedKey() {
        byte[] wav = {1, 2};
        String savedKey = "local/songs/generated/test.wav";
        when(audio.uploadWav(anyString(), same(wav))).thenReturn(savedKey);
        when(audio.getPresignedUrl(savedKey)).thenReturn("https://audio.test/song");
        var result = storage().upload(wav);
        assertThat(result.audioKey()).isEqualTo(savedKey);
        assertThat(result.audioUrl()).isEqualTo("https://audio.test/song");
        verify(audio).uploadWav(matches("songs/generated/[0-9a-f-]{36}\\.wav"), same(wav));
        verify(audio).getPresignedUrl(savedKey);
        verifyNoInteractions(s3);
    }
}

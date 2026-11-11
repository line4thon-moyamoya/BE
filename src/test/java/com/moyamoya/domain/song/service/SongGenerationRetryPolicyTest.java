package com.moyamoya.domain.song.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClientResponseException;
import software.amazon.awssdk.services.s3.model.S3Exception;

class SongGenerationRetryPolicyTest {
    @ParameterizedTest
    @ValueSource(ints = {408, 429, 500, 502, 503, 504})
    void retriesTemporaryHttpAndS3Failures(int status) {
        var http = new RestClientResponseException("temporary", status, "error", null, null, null);
        assertThat(SongGenerationRetryPolicy.isTransient(new IllegalStateException(http))).isTrue();
        assertThat(SongGenerationRetryPolicy.isTransient(
                new IllegalStateException(S3Exception.builder().statusCode(status).build()))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 413, 422, 501, 505})
    void doesNotRetryPermanentHttpAndS3Failures(int status) {
        var http = new RestClientResponseException("permanent", status, "error", null, null, null);
        assertThat(SongGenerationRetryPolicy.isTransient(http)).isFalse();
        assertThat(SongGenerationRetryPolicy.isTransient(S3Exception.builder().statusCode(status).build())).isFalse();
    }

    @Test
    void followsWrappedNetworkCausesButNotGenericIoOrValidationFailures() {
        assertThat(SongGenerationRetryPolicy.isTransient(
                new IllegalStateException(new IOException(new SocketTimeoutException())))).isTrue();
        assertThat(SongGenerationRetryPolicy.isTransient(new IllegalStateException(new ConnectException()))).isTrue();
        assertThat(SongGenerationRetryPolicy.isTransient(new IllegalStateException(new IOException("credentials")))).isFalse();
        assertThat(SongGenerationRetryPolicy.isTransient(new IllegalStateException("invalid words"))).isFalse();
        assertThat(SongGenerationRetryPolicy.isTransient(new IllegalArgumentException("invalid audio"))).isFalse();
        assertThat(SongGenerationRetryPolicy.isTransient(new javax.net.ssl.SSLException("certificate"))).isFalse();
    }

    @Test
    void classifiesGoogleCodesWithoutRetryingAuthenticationOrQuotaErrors() {
        for (var code : StatusCode.Code.values()) {
            ApiException exception = mock(ApiException.class);
            StatusCode status = mock(StatusCode.class);
            when(exception.getStatusCode()).thenReturn(status);
            when(status.getCode()).thenReturn(code);
            boolean expected = code == StatusCode.Code.UNAVAILABLE || code == StatusCode.Code.DEADLINE_EXCEEDED
                    || code == StatusCode.Code.INTERNAL;
            assertThat(SongGenerationRetryPolicy.isTransient(new IllegalStateException(exception)))
                    .as("Google status %s", code).isEqualTo(expected);
        }
    }
}

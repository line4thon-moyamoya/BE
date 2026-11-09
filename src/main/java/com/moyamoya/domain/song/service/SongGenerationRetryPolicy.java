package com.moyamoya.domain.song.service;

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import org.springframework.web.client.RestClientResponseException;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

final class SongGenerationRetryPolicy {
    private SongGenerationRetryPolicy() {}

    static boolean isTransient(Throwable exception) {
        var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (Throwable cause = exception; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof InterruptedException || cause instanceof javax.net.ssl.SSLException) {
                return false;
            }
            if (cause instanceof RestClientResponseException response) {
                return retryableStatus(response.getStatusCode().value());
            }
            if (cause instanceof AwsServiceException response) {
                return retryableStatus(response.statusCode());
            }
            if (cause instanceof ApiException response) {
                var code = response.getStatusCode().getCode();
                return code == StatusCode.Code.UNAVAILABLE
                        || code == StatusCode.Code.DEADLINE_EXCEEDED
                        || code == StatusCode.Code.INTERNAL;
            }
            if (cause instanceof SocketTimeoutException || cause instanceof ConnectException
                    || cause instanceof SocketException || cause instanceof HttpTimeoutException
                    || cause instanceof ApiCallTimeoutException || cause instanceof ApiCallAttemptTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static boolean retryableStatus(int status) {
        return status == 408 || status == 429 || status == 500
                || status == 502 || status == 503 || status == 504;
    }
}

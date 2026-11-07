package com.moyamoya.domain.question.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

public final class AudioMimeTypeSupport {

    private static final long MAX_AUDIO_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "audio/wav",
            "audio/mpeg",
            "audio/webm",
            "audio/mp4"
    );

    private AudioMimeTypeSupport() {
    }

    public static String validateAndResolveForAnalyze(
            MultipartFile audio
    ) {
        if (audio == null || audio.isEmpty()) {
            throw new IllegalArgumentException("음성 파일이 없습니다.");
        }

        if (audio.getSize() > MAX_AUDIO_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "음성 파일 크기가 10MB를 초과했습니다."
            );
        }

        byte[] audioBytes;
        try {
            audioBytes = audio.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "음성 파일을 읽을 수 없습니다.",
                    exception
            );
        }

        return validateAndResolve(audio.getContentType(), audioBytes);
    }

    public static String validateAndResolve(
            String declaredContentType,
            byte[] audioBytes
    ) {
        if (audioBytes == null || audioBytes.length == 0) {
            throw new IllegalArgumentException(
                    "업로드할 음성 데이터가 비어 있습니다."
            );
        }

        String normalizedDeclared = normalizeContentType(
                declaredContentType
        );
        String detected = detectContentType(audioBytes);

        if (detected == null) {
            throw new IllegalArgumentException(
                    "지원하지 않는 음성 형식입니다."
            );
        }

        if (normalizedDeclared != null && detected != null
                && !normalizedDeclared.equals(detected)) {
            throw new IllegalArgumentException(
                    "요청 Content-Type과 실제 음성 형식이 일치하지 않습니다."
            );
        }

        String resolved = normalizedDeclared != null
                ? normalizedDeclared
                : detected;

        if (resolved == null || !SUPPORTED_CONTENT_TYPES.contains(resolved)) {
            throw new IllegalArgumentException(
                    "지원하지 않는 음성 형식입니다."
            );
        }

        return resolved;
    }

    public static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }

        String normalized = contentType
                .split(";")[0]
                .trim()
                .toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "audio/wav", "audio/x-wav", "audio/wave" -> "audio/wav";
            case "audio/mpeg", "audio/mp3" -> "audio/mpeg";
            case "audio/webm" -> "audio/webm";
            case "audio/mp4", "audio/m4a", "audio/x-m4a" -> "audio/mp4";
            default -> null;
        };
    }

    public static String extensionOf(String normalizedContentType) {
        return switch (normalizedContentType) {
            case "audio/wav" -> "wav";
            case "audio/mpeg" -> "mp3";
            case "audio/webm" -> "webm";
            case "audio/mp4" -> "m4a";
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 음성 형식입니다."
            );
        };
    }

    private static String detectContentType(byte[] audioBytes) {
        if (isWav(audioBytes)) {
            return "audio/wav";
        }
        if (isMp3(audioBytes)) {
            return "audio/mpeg";
        }
        if (isWebm(audioBytes)) {
            return "audio/webm";
        }
        if (isMp4(audioBytes)) {
            return "audio/mp4";
        }
        return null;
    }

    private static boolean isWav(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'A'
                && bytes[10] == 'V'
                && bytes[11] == 'E';
    }

    private static boolean isMp3(byte[] bytes) {
        if (bytes.length >= 3
                && bytes[0] == 'I'
                && bytes[1] == 'D'
                && bytes[2] == '3') {
            return true;
        }

        return bytes.length >= 2
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xE0) == 0xE0;
    }

    private static boolean isWebm(byte[] bytes) {
        return bytes.length >= 4
                && (bytes[0] & 0xFF) == 0x1A
                && (bytes[1] & 0xFF) == 0x45
                && (bytes[2] & 0xFF) == 0xDF
                && (bytes[3] & 0xFF) == 0xA3;
    }

    private static boolean isMp4(byte[] bytes) {
        if (bytes.length < 12) {
            return false;
        }

        return bytes[4] == 'f'
                && bytes[5] == 't'
                && bytes[6] == 'y'
                && bytes[7] == 'p';
    }
}

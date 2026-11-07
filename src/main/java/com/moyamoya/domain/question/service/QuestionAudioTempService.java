package com.moyamoya.domain.question.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class QuestionAudioTempService {

    // 원본 음성 임시 저장
    public Path save(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new IllegalArgumentException("음성 파일이 없습니다.");
        }

        Path tempFile = null;

        try {
            // 서버 임시 디렉터리에 고유 파일 생성
            tempFile = Files.createTempFile(
                    "moyamoya-question-" + UUID.randomUUID(),
                    ".tmp"
            );

            // MultipartFile은 요청 종료 후 사라질 수 있으므로 복사
            try (var input = audio.getInputStream()) {
                Files.copy(
                        input,
                        tempFile,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }

            return tempFile;

        } catch (IOException e) {
            delete(tempFile);
            throw new IllegalStateException(
                    "질문 원본 음성 임시 저장에 실패했습니다.", e
            );
        }
    }

    // S3 업로드 시 원본 파일 읽기
    public byte[] read(Path path) {
        if (path == null) {
            throw new IllegalStateException("원본 음성이 없습니다.");
        }

        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "질문 원본 음성을 읽을 수 없습니다.", e
            );
        }
    }

    // 임시 파일 삭제
    public void delete(Path path) {
        if (path == null) {
            return;
        }

        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // 삭제 실패는 로그로 남기고 별도 정리 대상으로 처리
            org.slf4j.LoggerFactory
                    .getLogger(QuestionAudioTempService.class)
                    .warn("임시 음성 파일 삭제 실패: {}", path, e);
        }
    }
}

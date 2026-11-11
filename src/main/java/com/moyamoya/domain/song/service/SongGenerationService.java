package com.moyamoya.domain.song.service;

import com.moyamoya.domain.song.entity.SongStatus;
import com.moyamoya.domain.song.dto.response.SongMixResponse;
import com.moyamoya.domain.song.repository.SongRepository;
import com.moyamoya.domain.song.repository.SongWordRepository;
import com.moyamoya.global.s3.S3AudioService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class SongGenerationService {
    private final SongRepository songs;
    private final SongWordRepository songWords;
    private final SongMixingService mixingService;
    private final S3AudioService audioService;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public SongGenerationService(SongRepository songs, SongWordRepository songWords,
            SongMixingService mixingService, S3AudioService audioService,
            PlatformTransactionManager transactionManager) {
        this.songs = songs;
        this.songWords = songWords;
        this.mixingService = mixingService;
        this.audioService = audioService;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void generate(Long songId) {
        String audioKey = null;
        try {
            List<String> words = readTransaction.execute(status -> loadWords(songId));
            if (words == null) {
                return;
            }
            var result = mixWithRetry(songId, words);
            audioKey = result.audioKey();
            String uploadedKey = audioKey;
            boolean ready = Boolean.TRUE.equals(writeTransaction.execute(status -> {
                var song = songs.findByIdForUpdate(songId).orElse(null);
                if (song == null || song.getStatus() != SongStatus.GENERATING) {
                    return false;
                }
                song.markAsReady(uploadedKey);
                return true;
            }));
            if (!ready) {
                deleteUnusedAudio(audioKey);
            }
        } catch (RuntimeException exception) {
            log.error("노래 생성 실패. songId={}", songId, exception);
            if (markFailed(songId) && audioKey != null) {
                deleteUnusedAudio(audioKey);
            }
        }
    }

    private SongMixResponse mixWithRetry(Long songId, List<String> words) {
        try {
            return mixingService.mix(words);
        } catch (RuntimeException exception) {
            if (!SongGenerationRetryPolicy.isTransient(exception) || Thread.currentThread().isInterrupted()) {
                throw exception;
            }
            log.warn("일시적인 노래 생성 오류로 1회 재시도합니다. songId={}", songId, exception);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("노래 생성 재시도 대기가 중단됐습니다.", interrupted);
            }
            return mixingService.mix(words);
        }
    }

    private List<String> loadWords(Long songId) {
        var song = songs.findById(songId).orElse(null);
        if (song == null || song.getStatus() != SongStatus.GENERATING) {
            return null;
        }
        var rows = songWords.findAllBySong_IdOrderByWordOrderAsc(songId);
        if (rows.size() != 5) {
            throw new IllegalStateException("노래에 배정된 단어 5개가 필요합니다.");
        }
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).getWordOrder() != index + 1) {
                throw new IllegalStateException("노래 단어 순서가 올바르지 않습니다.");
            }
        }
        return rows.stream().map(row -> row.getLearnedWord().getWord().getWord()).toList();
    }

    public boolean markFailed(Long songId) {
        try {
            return Boolean.TRUE.equals(writeTransaction.execute(status -> {
                var song = songs.findByIdForUpdate(songId).orElse(null);
                if (song == null || song.getStatus() != SongStatus.GENERATING) {
                    return false;
                }
                song.markAsFailed();
                return true;
            }));
        } catch (RuntimeException exception) {
            log.error("노래 실패 상태 저장 실패. songId={}", songId, exception);
            return false;
        }
    }

    private void deleteUnusedAudio(String key) {
        try {
            audioService.deleteObject(key);
        } catch (RuntimeException exception) {
            log.error("미사용 노래 음원 정리 실패. key={}", key, exception);
        }
    }
}

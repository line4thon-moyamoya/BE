package com.moyamoya.domain.song.service;

import com.moyamoya.domain.song.model.SongGenerationRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SongGenerationListener {
    private final SongGenerationExecutor executor;
    private final SongGenerationService generationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(SongGenerationRequested event) {
        try {
            executor.execute(() -> generationService.generate(event.songId()));
        } catch (RuntimeException exception) {
            log.error("노래 생성 작업 등록 실패. songId={}", event.songId(), exception);
            generationService.markFailed(event.songId());
        }
    }
}

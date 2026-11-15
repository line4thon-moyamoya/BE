package com.moyamoya.domain.song.service;

import com.moyamoya.domain.song.model.SongWordAudioPreparationRequested;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SongWordAudioPreparationListener {
    private final SongWordAudioPreparationExecutor executor;
    private final SongWordAudioService wordAudio;
    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(SongWordAudioPreparationRequested event) {
        if (!pending.add(event.word())) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    wordAudio.prepare(event.word());
                } catch (RuntimeException exception) {
                    log.warn("노래 단어 음원 사전 생성 실패. word={}", event.word(), exception);
                } finally {
                    pending.remove(event.word());
                }
            });
        } catch (RuntimeException exception) {
            pending.remove(event.word());
            log.warn("노래 단어 음원 사전 생성 작업 등록 실패. word={}", event.word(), exception);
        }
    }
}

package com.moyamoya.domain.question.session;

import com.moyamoya.domain.question.service.QuestionAudioTempService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class QuestionSessionStore {

    private final QuestionAudioTempService audioTempService;

    private final Map<UUID, QuestionSession> sessions =
            new ConcurrentHashMap<>();

    public void save(QuestionSession session) {
        sessions.put(session.getSessionId(), session);
    }

    public Optional<QuestionSession> find(UUID sessionId) {
        QuestionSession session = sessions.get(sessionId);

        if (session == null) {
            return Optional.empty();
        }

        synchronized (session) {
            if (sessions.get(sessionId) != session) {
                return Optional.empty();
            }

            if (session.isExpired()) {
                // 만료된 세션과 임시 파일을 함께 정리
                removeIfSame(sessionId, session);
                return Optional.empty();
            }

            return Optional.of(session);
        }
    }

    public void remove(UUID sessionId) {
        QuestionSession session = sessions.get(sessionId);

        if (session == null) {
            return;
        }

        synchronized (session) {
            removeIfSame(sessionId, session);
        }
    }

    @Scheduled(
            fixedDelayString =
                    "${question.session.cleanup-delay-ms:300000}"
    )
    public void removeExpiredSessions() {
        sessions.forEach((sessionId, session) -> {
            synchronized (session) {
                if (session.isExpired()) {
                    removeIfSame(sessionId, session);
                }
            }
        });
    }

    public boolean isCurrent(QuestionSession session) {
        return sessions.get(session.getSessionId()) == session
                && !session.isExpired();
    }

    // 다른 세션 객체로 교체된 경우 잘못 삭제하지 않도록 확인
    private void removeIfSame(
            UUID sessionId,
            QuestionSession session
    ) {
        if (sessions.remove(sessionId, session)) {
            audioTempService.delete(
                    session.getOriginalAudioTempPath()
            );
        }
    }
}

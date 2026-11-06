package com.moyamoya.domain.question.session;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class QuestionSessionStore {

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

        if (session.isExpired()) {
            sessions.remove(sessionId);
            return Optional.empty();
        }

        return Optional.of(session);
    }

    public void remove(UUID sessionId) {
        sessions.remove(sessionId);
    }

    @Scheduled(fixedDelayString = "${question.session.cleanup-delay-ms:300000}")
    public void removeExpiredSessions() {
        sessions.entrySet().removeIf(
                entry -> entry.getValue().isExpired()
        );
    }
}

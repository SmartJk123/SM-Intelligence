package com.smi.assistant_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConversationMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryService.class);

    private static final int MAX_TURNS = 6; // 3 user + 3 assistant turns
    private static final long TTL_MILLIS = 30 * 60 * 1000L; // 30 minutes

    public record ChatTurn(String role, String content) {}

    private static class SessionHistory {
        private final List<ChatTurn> turns = new ArrayList<>();
        private Instant lastAccessed = Instant.now();

        public synchronized void addTurn(String role, String content) {
            this.lastAccessed = Instant.now();
            turns.add(new ChatTurn(role, content));
            while (turns.size() > MAX_TURNS) {
                turns.remove(0);
            }
        }

        public synchronized List<ChatTurn> getTurns() {
            this.lastAccessed = Instant.now();
            return new ArrayList<>(turns);
        }

        public synchronized boolean isExpired() {
            return Instant.now().toEpochMilli() - lastAccessed.toEpochMilli() > TTL_MILLIS;
        }
    }

    private final Map<String, SessionHistory> cache = new ConcurrentHashMap<>();

    public void recordTurn(String conversationId, String role, String content) {
        if (conversationId == null || conversationId.isBlank() || content == null || content.isBlank()) {
            return;
        }
        cleanupExpiredSessions();
        cache.computeIfAbsent(conversationId, id -> new SessionHistory())
                .addTurn(role, content);
    }

    public List<ChatTurn> getHistory(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Collections.emptyList();
        }
        cleanupExpiredSessions();
        SessionHistory session = cache.get(conversationId);
        if (session != null) {
            return session.getTurns();
        }
        return Collections.emptyList();
    }

    public void clearConversation(String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            SessionHistory removed = cache.remove(conversationId);
            if (removed != null) {
                log.info("Cleared conversation memory for {}", conversationId);
            }
        }
    }

    private void cleanupExpiredSessions() {
        if (cache.size() > 50) { // Only clean when cache reaches modest size
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }
    }
}

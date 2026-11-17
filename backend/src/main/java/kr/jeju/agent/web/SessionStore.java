package kr.jeju.agent.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionStore {

    private final Duration timeToLive;
    private final Clock clock;
    private final ConcurrentMap<String, Instant> lastAccessedAt = new ConcurrentHashMap<>();

    @Autowired
    public SessionStore(@Value("${app.session.ttl-minutes:30}") long ttlMinutes) {
        this(Duration.ofMinutes(ttlMinutes), Clock.systemUTC());
    }

    SessionStore(Duration timeToLive, Clock clock) {
        if (timeToLive.isZero() || timeToLive.isNegative()) {
            throw new IllegalArgumentException("세션 만료 시간은 0보다 커야 합니다.");
        }
        this.timeToLive = timeToLive;
        this.clock = clock;
    }

    public void touch(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("세션 ID는 비어 있을 수 없습니다.");
        }
        lastAccessedAt.put(sessionId, clock.instant());
    }

    public Optional<Instant> lastAccessedAt(String sessionId) {
        Instant lastAccess = lastAccessedAt.get(sessionId);
        if (lastAccess == null) {
            return Optional.empty();
        }
        if (isExpired(lastAccess)) {
            lastAccessedAt.remove(sessionId, lastAccess);
            return Optional.empty();
        }
        return Optional.of(lastAccess);
    }

    public boolean isActive(String sessionId) {
        return lastAccessedAt(sessionId).isPresent();
    }

    public void clear(String sessionId) {
        lastAccessedAt.remove(sessionId);
    }

    public Set<String> expireInactiveSessions() {
        Instant now = clock.instant();
        Set<String> expiredSessionIds = new HashSet<>();

        lastAccessedAt.forEach((sessionId, lastAccess) -> {
            if (!now.isBefore(lastAccess.plus(timeToLive))
                    && lastAccessedAt.remove(sessionId, lastAccess)) {
                expiredSessionIds.add(sessionId);
            }
        });

        return Set.copyOf(expiredSessionIds);
    }

    @Scheduled(fixedDelayString = "${app.session.cleanup-interval-ms:60000}")
    public void cleanupExpiredSessions() {
        expireInactiveSessions();
    }

    private boolean isExpired(Instant lastAccess) {
        return !clock.instant().isBefore(lastAccess.plus(timeToLive));
    }
}

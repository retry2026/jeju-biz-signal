package kr.jeju.agent.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionStoreTest {

    private MutableClock clock;
    private SessionStore sessionStore;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-16T00:00:00Z"));
        sessionStore = new SessionStore(Duration.ofMinutes(30), clock);
    }

    @Test
    void 세션을_생성하고_마지막_접근_시각을_조회한다() {
        sessionStore.touch("session-a");

        assertThat(sessionStore.lastAccessedAt("session-a")).contains(clock.instant());
        assertThat(sessionStore.isActive("session-a")).isTrue();
    }

    @Test
    void 세션별_상태가_분리되고_초기화할_수_있다() {
        sessionStore.touch("session-a");
        clock.advance(Duration.ofMinutes(5));
        sessionStore.touch("session-b");

        assertThat(sessionStore.lastAccessedAt("session-a"))
                .contains(Instant.parse("2026-11-16T00:00:00Z"));
        assertThat(sessionStore.lastAccessedAt("session-b")).contains(clock.instant());

        sessionStore.clear("session-a");

        assertThat(sessionStore.lastAccessedAt("session-a")).isEmpty();
        assertThat(sessionStore.isActive("session-b")).isTrue();
    }

    @Test
    void 접근할_때마다_만료_시각이_갱신된다() {
        sessionStore.touch("session-a");
        clock.advance(Duration.ofMinutes(29));
        sessionStore.touch("session-a");
        clock.advance(Duration.ofMinutes(29));

        assertThat(sessionStore.isActive("session-a")).isTrue();
        assertThat(sessionStore.expireInactiveSessions()).isEmpty();
    }

    @Test
    void 마지막_접근으로부터_정확히_30분이_지나면_세션을_만료한다() {
        sessionStore.touch("session-a");
        clock.advance(Duration.ofMinutes(30));

        assertThat(sessionStore.expireInactiveSessions()).containsExactly("session-a");
        assertThat(sessionStore.isActive("session-a")).isFalse();
        assertThat(sessionStore.lastAccessedAt("session-a")).isEmpty();
    }

    private static class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

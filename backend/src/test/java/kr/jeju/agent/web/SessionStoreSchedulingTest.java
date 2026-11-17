package kr.jeju.agent.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import kr.jeju.agent.JejuAgentApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.util.ReflectionTestUtils;

class SessionStoreSchedulingTest {

    @Test
    void application_설정으로_세션_빈과_예약_정리_작업을_등록한다() throws InterruptedException {
        assertThat(JejuAgentApplication.class.isAnnotationPresent(EnableScheduling.class)).isTrue();

        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(SchedulingConfiguration.class)
                .withPropertyValues("app.session.cleanup-interval-ms=10")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getEnvironment().getProperty("app.session.ttl-minutes"))
                            .isEqualTo("30");

                    SessionStore sessionStore = context.getBean(SessionStore.class);
                    assertThat(ReflectionTestUtils.getField(sessionStore, "timeToLive"))
                            .isEqualTo(Duration.ofMinutes(30));
                    assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class)
                            .getScheduledTasks()).hasSize(1);

                    sessionStore.touch("expired-session");
                    metadata(sessionStore).put(
                            "expired-session",
                            Instant.now().minus(Duration.ofMinutes(31)));

                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                    while (metadata(sessionStore).containsKey("expired-session")
                            && System.nanoTime() < deadline) {
                        Thread.sleep(10);
                    }

                    assertThat(metadata(sessionStore)).doesNotContainKey("expired-session");
                });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Instant> metadata(SessionStore sessionStore) {
        return (Map<String, Instant>) ReflectionTestUtils.getField(sessionStore, "lastAccessedAt");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @Import(SessionStore.class)
    static class SchedulingConfiguration {
    }
}

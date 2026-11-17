package kr.jeju.agent.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import kr.jeju.agent.agent.AgentAnswer;
import kr.jeju.agent.agent.AgentRequest;
import kr.jeju.agent.agent.ChatAgent;
import kr.jeju.agent.agent.LlmUnavailableException;
import kr.jeju.agent.chart.Chart;
import kr.jeju.agent.chart.ChartDataset;

class ChatControllerTest {

    static final String BODY = """
            {"sessionId":"s1","userType":"EXISTING","area":"%s","industry":"%s",
             "myStore":{"monthlySales":[{"month":"2026-10","amount":28000000}]},
             "message":"작년보다 매출이 줄었어요"}""";

    final AtomicReference<AgentRequest> received = new AtomicReference<>();
    final List<String> ended = new ArrayList<>();
    boolean llmDown;
    SessionStore sessions;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        ChatAgent agent = new ChatAgent() {
            @Override
            public AgentAnswer answer(AgentRequest request) {
                if (llmDown) {
                    throw new LlmUnavailableException("시간 초과", null);
                }
                received.set(request);
                return new AgentAnswer("ANSWER", "요약", List.of(new AgentAnswer.Section("customers", "동네 손님", "본문")),
                        List.of(new Chart("customerMix", "doughnut", "손님 구성", List.of("도민", "관광객"),
                                List.of(new ChartDataset("비중", java.util.Arrays.asList(40.0, null), null)), "%", "S3", "AREA")),
                        List.of(new AgentAnswer.Evidence("areaCustomers", "S3", "2025-10~2026-09", "AREA", false)),
                        List.of("과거 데이터 기반이며 예측이 아닙니다."), List.of("다음 질문"), null);
            }

            @Override
            public void endSession(String sessionId) {
                ended.add(sessionId);
            }
        };
        MetaController meta = new MetaController(null) {
            @Override
            Set<String> areaCodes() {
                return Set.of("50110250");
            }
        };
        sessions = new SessionStore(Duration.ofMinutes(30), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        mvc = MockMvcBuilders.standaloneSetup(new ChatController(agent, sessions, meta))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void 화면_요청을_에이전트_입력으로_바꾸고_답을_계약_형식으로_돌려준다() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(BODY.formatted("50110250", "CAFE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ANSWER"))
                .andExpect(jsonPath("$.sections[0].id").value("customers"))
                .andExpect(jsonPath("$.charts[0].datasets[0].data[0]").value(40.0))
                .andExpect(jsonPath("$.charts[0].datasets[0].data[1]").doesNotExist())
                .andExpect(jsonPath("$.evidence[0].scope").value("AREA"))
                .andExpect(jsonPath("$.askFor").doesNotExist());

        assertThat(received.get().monthlySales()).containsExactly(new AgentRequest.MonthlySale("2026-10", 28_000_000L));
        assertThat(sessions.isActive("s1")).isTrue();
    }

    @Test
    void 목록에_없는_업종이나_지역은_400() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(BODY.formatted("50110250", "PIZZA")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("industry"));
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(BODY.formatted("99999999", "CAFE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.field").value("area"));
        assertThat(received.get()).isNull();
    }

    @Test
    void LLM_오류는_503_LLM_UNAVAILABLE() throws Exception {
        llmDown = true;
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(BODY.formatted("50110250", "CAFE")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LLM_UNAVAILABLE"));
    }

    @Test
    void 만료된_세션은_에이전트_대화_기억도_지운다() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.EPOCH);
        Clock clock = new Clock() {
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId zone) { return this; }
            public Instant instant() { return now.get(); }
        };
        SessionStore expiring = new SessionStore(Duration.ofMinutes(30), clock);
        expiring.onExpire(ended::add);
        expiring.touch("old");
        now.set(Instant.EPOCH.plus(Duration.ofMinutes(31)));
        expiring.cleanupExpiredSessions();

        assertThat(ended).containsExactly("old");
    }
}

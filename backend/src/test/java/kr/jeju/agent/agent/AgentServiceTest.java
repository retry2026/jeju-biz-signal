package kr.jeju.agent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import kr.jeju.agent.chart.ChartAssembler;

/** LLM 없이 에이전트 흐름을 확인한다. 지표는 ②의 실제 클래스를 메모리 DuckDB로 돌리고, LLM 자리만 가짜로 바꾼다. */
class AgentServiceTest {

    static final String AEWOL = AgentTestDb.AEWOL;
    static AgentTestDb db;

    @BeforeAll
    static void setUp() throws Exception {
        db = new AgentTestDb();
    }

    @AfterAll
    static void tearDown() throws Exception {
        db.close();
    }

    static AgentRequest request(String userType, String message, List<AgentRequest.MonthlySale> sales) {
        return new AgentRequest("s1", userType, AEWOL, "CAFE", sales, message);
    }

    static AgentService service(LlmGateway llm) {
        return new AgentService(llm, db.competition, db.areaCustomers, db.industryTourism, new ChartAssembler(),
                "system", 6);
    }

    /** 시나리오 B처럼 도구 2개를 부르고 차트 4개를 고르는 가짜 LLM. */
    static LlmGateway scenarioB(AtomicInteger calls) {
        return (sessionId, system, user, tools) -> {
            calls.incrementAndGet();
            AgentTools t = (AgentTools) tools;
            t.areaCustomers(AEWOL, null);
            t.industryTourism("CAFE", null);
            return new AgentReply("ANSWER", "애월은 관광객 비중이 높습니다.",
                    List.of(new AgentReply.SectionText("customers", "동네 손님", "본문")),
                    List.of("customerMix", "industrySeason", "touristFlow", "ticketSize"),
                    List.of("외국인 손님은 어느 나라가 많나요?", "a", "b", "c"), null);
        };
    }

    @Test
    void 답변이면_차트_근거_한계를_실제_지표_결과로_조립한다() {
        AtomicInteger calls = new AtomicInteger();
        AgentAnswer r = service(scenarioB(calls)).answer(request("EXISTING", "겨울만 되면 매출이 반토막 나요. 정상인가요?", null));

        assertThat(r.type()).isEqualTo("ANSWER");
        assertThat(r.charts()).extracting(c -> c.id()).containsExactly("customerMix", "industrySeason", "touristFlow");
        List<Double> mix = r.charts().get(0).datasets().get(0).data();
        assertThat(mix.get(0) + mix.get(1) + mix.get(2)).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.2));
        assertThat(mix.get(1)).isGreaterThan(mix.get(0)); // 애월은 내국인 관광객 > 도민
        assertThat(r.evidence()).extracting(e -> e.tool()).containsExactly("areaCustomers", "industryTourism");
        assertThat(r.evidence().get(0).scope()).isEqualTo("AREA");
        assertThat(r.evidence().get(1).scope()).isEqualTo("JEJU");
        assertThat(r.limits()).contains("동네 손님 구성은 음식점업 전체 기준입니다.",
                "업종 성격은 동네가 아닌 제주 전체 기준입니다.", "과거 데이터 기반이며 예측이 아닙니다.");
        assertThat(r.suggestions()).hasSize(3);
        assertThat(r.askFor()).isNull();
    }

    @Test
    void 지역이_비면_LLM을_부르지_않고_되묻는다() {
        AtomicInteger calls = new AtomicInteger();
        AgentAnswer r = service(scenarioB(calls)).answer(new AgentRequest("s1", "STARTUP", " ", "CAFE", null, "괜찮을까요?"));

        assertThat(r.type()).isEqualTo("ASK_BACK");
        assertThat(r.askFor()).isEqualTo("AREA");
        assertThat(calls.get()).isZero();
    }

    @Test
    void 기존_사업자가_전년_비교를_묻는데_월매출이_없으면_입력을_요청한다() {
        AtomicInteger calls = new AtomicInteger();
        AgentAnswer r = service(scenarioB(calls)).answer(request("EXISTING", "작년보다 매출이 줄었는데 우리 가게 문제인가요?", null));

        assertThat(r.type()).isEqualTo("ASK_BACK");
        assertThat(r.askFor()).isEqualTo("MY_STORE");
        assertThat(calls.get()).isZero();
    }

    @Test
    void 거절이면_차트와_근거와_한계를_비운다() {
        LlmGateway refuse = (s, sys, u, t) -> new AgentReply("REFUSE", "시간대 데이터는 없습니다.", List.of(),
                List.of("customerMix"), List.of("손님 구성을 볼까요?"), null);
        AgentAnswer r = service(refuse).answer(request("STARTUP", "저녁 장사 어때요?", null));

        assertThat(r.type()).isEqualTo("REFUSE");
        assertThat(r.charts()).isEmpty();
        assertThat(r.evidence()).isEmpty();
        assertThat(r.limits()).isEmpty();
        assertThat(r.suggestions()).containsExactly("손님 구성을 볼까요?");
    }

    @Test
    void 월매출을_넣으면_차트가_내_가게와_동네_전년_대비를_나란히_만든다() {
        List<AgentRequest.MonthlySale> sales = List.of(
                new AgentRequest.MonthlySale("2025-10", 33_000_000L), new AgentRequest.MonthlySale("2026-10", 28_000_000L));
        LlmGateway llm = (s, sys, user, tools) -> {
            assertThat(user).contains("2026-10=28000000").doesNotContain("%");
            ((AgentTools) tools).areaCustomers(AEWOL, null);
            return new AgentReply("ANSWER", "요약", List.of(), List.of("myStoreVsArea"), List.of(), null);
        };
        AgentAnswer r = service(llm).answer(request("EXISTING", "작년보다 매출이 줄었어요", sales));

        assertThat(r.charts()).hasSize(1);
        List<Double> data = r.charts().get(0).datasets().get(0).data();
        assertThat(data.get(0)).isEqualTo(-15.2);   // 내 가게: 28/33 - 1
        assertThat(data.get(1)).isEqualTo(-10.0);   // 동네: 2026년이 작년보다 10% 적게 넣은 데이터
    }

    @Test
    void 지표가_실패하면_예외_대신_LLM이_읽을_문장을_돌려준다() {
        EvidenceCollector collector = new EvidenceCollector();
        AgentTools tools = new AgentTools(db.competition, db.areaCustomers, db.industryTourism, collector, 6);

        Object result = tools.areaCustomers("99999999", null);

        assertThat(result).isInstanceOf(String.class).asString().contains("데이터가 없어");
        assertThat(collector.callCount()).isZero();
    }

    @Test
    void 도구_호출이_한도를_넘으면_문장으로_막는다() {
        EvidenceCollector collector = new EvidenceCollector();
        AgentTools tools = new AgentTools(db.competition, db.areaCustomers, db.industryTourism, collector, 2);
        tools.areaCustomers(AEWOL, null);
        tools.competition(AEWOL, "CAFE", 500);

        assertThat(tools.industryTourism("CAFE", null)).asString().contains("한도");
        assertThat(collector.callCount()).isEqualTo(2);
    }

    @Test
    void LLM_오류는_LlmUnavailableException으로_전달된다() {
        assertThatThrownBy(() -> service(new NoLlmGateway()).answer(request("STARTUP", "괜찮을까요?", null)))
                .isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void LLM이_한_번_실패하면_다시_부르고_근거는_한_번만_남긴다() {
        AtomicInteger calls = new AtomicInteger();
        LlmGateway ok = scenarioB(new AtomicInteger());
        LlmGateway flaky = (s, sys, u, t) -> {
            if (calls.incrementAndGet() == 1) {
                ((AgentTools) t).areaCustomers(AEWOL, null);
                throw new LlmUnavailableException("503", new RuntimeException("overloaded"));
            }
            return ok.ask(s, sys, u, t);
        };
        AgentAnswer r = service(flaky).answer(request("EXISTING", "겨울만 되면 매출이 반토막 나요", null));

        assertThat(calls.get()).isEqualTo(2);
        assertThat(r.evidence()).extracting(e -> e.tool()).containsExactly("areaCustomers", "industryTourism");
    }

    @Test
    void 이상한_type은_ANSWER로_바꾼다() {
        LlmGateway llm = (s, sys, u, t) -> new AgentReply("WHATEVER", "요약", null, null, null, null);
        AgentAnswer r = service(llm).answer(request("STARTUP", "괜찮을까요?", null));

        assertThat(r.type()).isEqualTo("ANSWER");
        assertThat(r.sections()).isEmpty();
        assertThat(r.charts()).isEmpty();
    }

    @Test
    void LLM_출력에는_숫자_필드가_없다() {
        for (RecordComponent c : AgentReply.class.getRecordComponents()) {
            assertThat(Number.class.isAssignableFrom(c.getType()) || c.getType().isPrimitive())
                    .as(c.getName()).isFalse();
        }
    }
}

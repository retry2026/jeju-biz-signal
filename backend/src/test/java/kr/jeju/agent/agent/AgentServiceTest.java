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
    void 거절이면_본문과_차트와_근거와_한계를_비운다() {
        LlmGateway refuse = (s, sys, u, t) -> new AgentReply("REFUSE", "시간대 데이터는 없습니다.",
                List.of(new AgentReply.SectionText("customers", "동네 손님", "관광객 비중은 69.6%입니다.")),
                List.of("customerMix"), List.of("손님 구성을 볼까요?"), null);
        AgentAnswer r = service(refuse).answer(request("STARTUP", "저녁 장사 어때요?", null));

        assertThat(r.type()).isEqualTo("REFUSE");
        assertThat(r.sections()).isEmpty();
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
    void 매출을_물었는데_LLM이_내_가게_차트를_빠뜨려도_맨_앞에_넣는다() {
        List<AgentRequest.MonthlySale> sales = List.of(
                new AgentRequest.MonthlySale("2026-05", 2_000_000L), new AgentRequest.MonthlySale("2025-03", 4_000_000L));
        LlmGateway llm = (s, sys, user, tools) -> {
            assertThat(user).contains("작년 같은 달 매출이 없어");
            ((AgentTools) tools).areaCustomers(AEWOL, null);
            return new AgentReply("ANSWER", "요약", List.of(), List.of("customerMix", "areaMonthly", "ticketSize"), List.of(), null);
        };
        AgentAnswer r = service(llm).answer(request("EXISTING", "동네 결제 흐름과 내 월매출 추이를 비교해줘", sales));

        assertThat(r.charts()).extracting(c -> c.id()).containsExactly("myStoreVsArea", "customerMix", "areaMonthly");
        assertThat(r.charts().get(0).labels()).containsExactly("2025-03", "2026-05");
    }

    @Test
    void 답변_문장에_섞인_차트_id와_업종_코드를_우리말로_바꾼다() {
        assertThat(AgentService.readable("`myStoreVsArea` 차트를 보면 PUB 업종은 touristShare가 낮습니다."))
                .isEqualTo("내 가게 비교 차트를 보면 주점 업종은 관광객 비중이 낮습니다.");
        assertThat(AgentService.readable("PUBLIC 데이터")).isEqualTo("PUBLIC 데이터");
        assertThat(AgentService.readable("yoyChange는 -3%, CAFE와 KOREAN을 비교")).isEqualTo("전년 대비 변화는 -3%, 카페와 한식을 비교");

        LlmGateway llm = (s, sys, u, t) -> new AgentReply("REFUSE", "areaMonthly는 없습니다.", List.of(),
                List.of(), List.of("CAFE 손님 구성은?"), null);
        AgentAnswer r = service(llm).answer(request("STARTUP", "메뉴 가격 알려줘", null));
        assertThat(r.summary()).isEqualTo("동네 월별 결제 차트는 없습니다.");
        assertThat(r.suggestions()).containsExactly("카페 손님 구성은?");
    }

    @Test
    void 직전_답변이_거절이면_다음_질문에_그_사실을_알려_준다() {
        List<String> prompts = new java.util.ArrayList<>();
        LlmGateway llm = (s, sys, user, t) -> {
            prompts.add(user);
            return new AgentReply("REFUSE", "메뉴 가격은 알 수 없어요.", List.of(), List.of(), List.of(), null);
        };
        AgentService service = service(llm);
        service.answer(request("STARTUP", "메뉴 가격 얼마로 해요?", null));
        service.answer(request("STARTUP", "구체적으로요?", null));
        service.endSession("s1");
        service.answer(request("STARTUP", "구체적으로요?", null));

        assertThat(prompts.get(0)).doesNotContain("[직전 답변]");
        assertThat(prompts.get(1)).contains("[직전 답변]", "REFUSE");
        assertThat(prompts.get(2)).doesNotContain("[직전 답변]");
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
    void 기간이_없는_점포_수_근거도_기간을_문자열로_채운다() {
        EvidenceCollector collector = new EvidenceCollector();
        new AgentTools(db.competition, db.areaCustomers, db.industryTourism, collector, 6).competition(AEWOL, "CAFE", 500);

        assertThat(collector.evidence()).singleElement()
                .satisfies(e -> assertThat(e.period()).isNotBlank());
    }

    @Test
    void LLM이_다른_업종을_넘겨도_고른_업종으로_계산한다() {
        LlmGateway wrongIndustry = (s, sys, u, t) -> {
            ((AgentTools) t).competition(AEWOL, "PUB", null);
            return new AgentReply("ANSWER", "요약", List.of(), List.of("competition"), List.of(), null);
        };
        AgentAnswer cafe = service(wrongIndustry).answer(request("STARTUP", "경쟁이 심한가요?", null));

        EvidenceCollector direct = new EvidenceCollector();
        new AgentTools(db.competition, db.areaCustomers, db.industryTourism, direct, 6).competition(AEWOL, "CAFE", null);
        assertThat(cafe.charts().get(0).datasets().get(0).data().get(0))
                .isEqualTo((double) direct.snapshot(null).competition().storeCount());
    }

    @Test
    void 고른_업종과_다른_업종을_물으면_업종을_바꾸라고_되묻는다() {
        AtomicInteger calls = new AtomicInteger();
        AgentAnswer r = service(scenarioB(calls)).answer(
                new AgentRequest("s1", "STARTUP", AEWOL, "PUB", null, "이 동네에 카페 차려도 괜찮을까요?"));

        assertThat(r.type()).isEqualTo("ASK_BACK");
        assertThat(r.askFor()).isEqualTo("INDUSTRY");
        assertThat(r.summary()).contains("주점", "카페로 바꿔");
        assertThat(InputGuard.withRo("주점")).isEqualTo("주점으로");
        assertThat(calls.get()).isZero();
        assertThat(InputGuard.otherIndustry(new AgentRequest("s", "EXISTING", AEWOL, "KOREAN", null,
                "연동에서 고깃집 하는데 카페 손님도 오나요?"))).isEmpty();
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
    void LLM이_계속_실패하면_예비_모델_순서대로_시도하고_마지막에_예외를_던진다() {
        List<Integer> attempts = new java.util.ArrayList<>();
        LlmGateway allDown = new LlmGateway() {
            @Override
            public AgentReply ask(String s, String sys, String u, Object t) {
                throw new AssertionError("시도 번호가 있는 메서드를 불러야 한다");
            }

            @Override
            public AgentReply ask(String s, String sys, String u, Object t, int attempt) {
                attempts.add(attempt);
                throw new LlmUnavailableException("429", new RuntimeException("quota"));
            }

            @Override
            public int maxAttempts() {
                return 3;
            }
        };

        assertThatThrownBy(() -> service(allDown).answer(request("STARTUP", "괜찮을까요?", null)))
                .isInstanceOf(LlmUnavailableException.class);
        assertThat(attempts).containsExactly(0, 1, 2);
    }

    @Test
    void 지역_이름을_알면_LLM에_이름과_코드를_함께_넘긴다() {
        AgentService named = new AgentService(new NoLlmGateway(), db.competition, db.areaCustomers, db.industryTourism,
                new ChartAssembler(), "system", 6, code -> AEWOL.equals(code) ? "한림읍" : null);

        assertThat(named.userMessage(request("STARTUP", "괜찮을까요?", null)))
                .contains("지역: 한림읍 (도구에 넘길 행정동 코드: " + AEWOL + ")");
        assertThat(service(new NoLlmGateway()).userMessage(request("STARTUP", "괜찮을까요?", null)))
                .contains("지역(행정동 코드): " + AEWOL);
    }

    @Test
    void 되묻기의_askFor가_정해진_값이_아니면_MESSAGE로_바꾼다() {
        LlmGateway vague = (s, sys, u, t) -> new AgentReply("ASK_BACK", "무엇이 궁금하신가요?", null, null,
                List.of("손님 구성을 볼까요?"), "QUESTION");
        AgentAnswer r = service(vague).answer(request("STARTUP", "분석해주세요", null));

        assertThat(r.type()).isEqualTo("ASK_BACK");
        assertThat(r.askFor()).isEqualTo("MESSAGE");
        assertThat(AgentService.askFor("MY_STORE")).isEqualTo("MY_STORE");
    }

    @Test
    void 내_가게를_가리키지_않는_전년_비교_질문은_월매출_없이_답한다() {
        AtomicInteger calls = new AtomicInteger();
        for (String q : List.of("요즘 제주 오는 관광객이 작년보다 줄었나요?", "겨울만 되면 매출이 줄어요. 정상인가요?",
                "동네 소비가 작년보다 줄었나요?")) {
            assertThat(service(scenarioB(calls)).answer(request("EXISTING", q, null)).type()).as(q).isEqualTo("ANSWER");
        }
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void 시간_초과는_다시_시도하지_않는다() {
        AtomicInteger calls = new AtomicInteger();
        LlmGateway slow = (s, sys, u, t) -> {
            calls.incrementAndGet();
            throw new LlmUnavailableException("시간 초과", new java.util.concurrent.TimeoutException());
        };

        assertThatThrownBy(() -> service(slow).answer(request("STARTUP", "괜찮을까요?", null)))
                .isInstanceOf(LlmUnavailableException.class);
        assertThat(calls.get()).isEqualTo(1);
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

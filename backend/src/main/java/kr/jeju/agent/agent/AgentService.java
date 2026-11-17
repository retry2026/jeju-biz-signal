package kr.jeju.agent.agent;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.jeju.agent.chart.ChartAssembler;
import kr.jeju.agent.chart.EvidenceSnapshot;
import kr.jeju.agent.chart.MonthlySale;
import kr.jeju.agent.chart.MyStoreInput;
import kr.jeju.agent.metric.AreaCustomerMetric;
import kr.jeju.agent.metric.CompetitionMetric;
import kr.jeju.agent.metric.IndustryTourismMetric;

/**
 * 질문 하나를 처리한다: 입력 검사 → LLM(도구 호출) → 차트, 근거, 한계 조립.
 * LLM은 문장과 차트 ID만 정하고, 숫자는 모두 도구 결과와 ②의 차트 조립기에서 나온다.
 */
public class AgentService implements ChatAgent {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final Set<String> TYPES = Set.of("ANSWER", "REFUSE", "ASK_BACK");
    private static final int MAX_CHARTS = 3;

    private final LlmGateway llm;
    private final CompetitionMetric competitionMetric;
    private final AreaCustomerMetric areaCustomerMetric;
    private final IndustryTourismMetric industryTourismMetric;
    private final ChartAssembler chartAssembler;
    private final String systemPrompt;
    private final int maxToolCalls;
    private final InputGuard inputGuard = new InputGuard();
    private final LimitBuilder limitBuilder = new LimitBuilder();

    public AgentService(LlmGateway llm, CompetitionMetric competitionMetric, AreaCustomerMetric areaCustomerMetric,
            IndustryTourismMetric industryTourismMetric, ChartAssembler chartAssembler, String systemPrompt,
            int maxToolCalls) {
        this.llm = llm;
        this.competitionMetric = competitionMetric;
        this.areaCustomerMetric = areaCustomerMetric;
        this.industryTourismMetric = industryTourismMetric;
        this.chartAssembler = chartAssembler;
        this.systemPrompt = systemPrompt;
        this.maxToolCalls = maxToolCalls;
    }

    @Override
    public AgentAnswer answer(AgentRequest request) {
        return answer(request, s -> { });
    }

    /** 평가 하네스용: 이번 요청에서 부른 도구 결과를 onEvidence로 넘겨 본문 숫자의 근거를 확인할 수 있게 한다. */
    public AgentAnswer answer(AgentRequest request, Consumer<EvidenceSnapshot> onEvidence) {
        Optional<AgentAnswer> guarded = inputGuard.check(request);
        if (guarded.isPresent()) {
            return guarded.get();
        }

        long start = System.currentTimeMillis();
        EvidenceCollector collector;
        AgentReply reply;
        int attempt = 1;
        while (true) {
            // 재시도마다 도구와 수집기를 새로 만들어 근거가 두 번 쌓이지 않게 한다.
            collector = new EvidenceCollector();
            AgentTools tools = new AgentTools(competitionMetric, areaCustomerMetric, industryTourismMetric,
                    collector, maxToolCalls);
            try {
                reply = llm.ask(request.sessionId(), systemPrompt, userMessage(request), tools);
                break;
            } catch (LlmUnavailableException e) {
                // Gemini가 가끔 503(과부하)을 준다. 시간 초과는 이미 오래 기다렸으니 다시 부르지 않는다.
                if (attempt++ >= 2 || e.getCause() instanceof java.util.concurrent.TimeoutException) {
                    throw e;
                }
                log.warn("LLM 실패, 한 번 다시 시도: {}", e.getCause() == null ? e.getMessage() : e.getCause().toString());
            }
        }
        AgentAnswer response = assemble(reply, collector, myStore(request));
        onEvidence.accept(collector.snapshot(myStore(request)));

        log.info("chat session={} type={} tools={} ms={}", request.sessionId(), response.type(),
                collector.evidence().stream().map(e -> e.tool()).toList(), System.currentTimeMillis() - start);
        return response;
    }

    @Override
    public void endSession(String sessionId) {
        llm.clear(sessionId);
    }

    AgentAnswer assemble(AgentReply reply, EvidenceCollector collector, MyStoreInput myStore) {
        String type = reply.type() != null && TYPES.contains(reply.type()) ? reply.type() : "ANSWER";
        List<AgentAnswer.Section> sections = orEmpty(reply.sections()).stream()
                .map(s -> new AgentAnswer.Section(s.id(), s.title(), s.text()))
                .toList();
        List<String> suggestions = orEmpty(reply.suggestions()).stream().limit(3).toList();

        if (!"ANSWER".equals(type)) {
            return new AgentAnswer(type, reply.summary(), "ASK_BACK".equals(type) ? List.of() : sections,
                    List.of(), List.of(), List.of(), suggestions, "ASK_BACK".equals(type) ? reply.askFor() : null);
        }

        List<String> chartIds = orEmpty(reply.chartIds()).stream().distinct().limit(MAX_CHARTS).toList();
        return new AgentAnswer(type, reply.summary(), sections,
                chartAssembler.build(chartIds, collector.snapshot(myStore)),
                collector.evidence(), limitBuilder.build(collector.metas()), suggestions, null);
    }

    /** 사용자 월매출 입력을 ②의 차트 조립기 입력으로 바꾼다. 전년 대비 계산은 차트 조립기 한 곳에서만 한다. */
    static MyStoreInput myStore(AgentRequest request) {
        List<MonthlySale> sales = request.monthlySales().stream()
                .filter(s -> s.month() != null && s.amount() != null)
                .map(s -> new MonthlySale(s.month(), s.amount()))
                .toList();
        return sales.isEmpty() ? null : new MyStoreInput(sales);
    }

    private static String userMessage(AgentRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("[조건]\n");
        sb.append("- 사용자 유형: ").append("EXISTING".equals(request.userType()) ? "기존 사업자" : "예비 창업자").append('\n');
        sb.append("- 지역(행정동 코드): ").append(request.area()).append('\n');
        sb.append("- 업종 코드: ").append(request.industry()).append('\n');
        if (!request.monthlySales().isEmpty()) {
            sb.append("- 사용자가 입력한 월매출(원): ");
            request.monthlySales().forEach(s -> sb.append(s.month()).append('=').append(s.amount()).append(' '));
            sb.append("\n  (이 숫자와 동네 결제 전년 대비를 비교할 때는 myStoreVsArea 차트를 고르고, 비율은 직접 계산하지 말고 '줄었다/늘었다'로만 말한다)\n");
        }
        sb.append("\n[질문]\n").append(request.message());
        // 작은 모델은 JSON 형식 지시를 보고 도구 없이 바로 답하는 경향이 있어 순서를 마지막에 다시 못박는다.
        sb.append("\n\n[순서]\n1. 데이터로 답할 수 있는 질문이면 JSON을 쓰기 전에 필요한 도구를 먼저 호출한다.")
                .append("\n2. 도구 결과를 받은 뒤에만 숫자를 쓴다. 도구를 부르지 않았으면 숫자를 하나도 쓰지 않는다.")
                .append("\n3. 데이터에 없는 질문이면 도구 없이 REFUSE로 답한다.");
        return sb.toString();
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}

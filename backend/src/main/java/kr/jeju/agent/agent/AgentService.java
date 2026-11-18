package kr.jeju.agent.agent;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.function.Consumer;
import java.util.function.Function;

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
    private static final long RETRY_DEADLINE_MS = 25_000;
    private static final Set<String> ASK_FOR = Set.of("AREA", "INDUSTRY", "MESSAGE", "MY_STORE");

    private final LlmGateway llm;
    private final CompetitionMetric competitionMetric;
    private final AreaCustomerMetric areaCustomerMetric;
    private final IndustryTourismMetric industryTourismMetric;
    private final ChartAssembler chartAssembler;
    private final String systemPrompt;
    private final int maxToolCalls;
    private final InputGuard inputGuard = new InputGuard();
    private final LimitBuilder limitBuilder = new LimitBuilder();

    /** 행정동 코드 → 화면 이름 (예: 50110660 → 노형동). 모르는 코드면 null. */
    private final Function<String, String> areaNames;

    public AgentService(LlmGateway llm, CompetitionMetric competitionMetric, AreaCustomerMetric areaCustomerMetric,
            IndustryTourismMetric industryTourismMetric, ChartAssembler chartAssembler, String systemPrompt,
            int maxToolCalls) {
        this(llm, competitionMetric, areaCustomerMetric, industryTourismMetric, chartAssembler, systemPrompt,
                maxToolCalls, code -> null);
    }

    public AgentService(LlmGateway llm, CompetitionMetric competitionMetric, AreaCustomerMetric areaCustomerMetric,
            IndustryTourismMetric industryTourismMetric, ChartAssembler chartAssembler, String systemPrompt,
            int maxToolCalls, Function<String, String> areaNames) {
        this.areaNames = areaNames;
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
        int attempt = 0;
        while (true) {
            // 재시도마다 도구와 수집기를 새로 만들어 근거가 두 번 쌓이지 않게 한다.
            collector = new EvidenceCollector();
            AgentTools tools = new AgentTools(competitionMetric, areaCustomerMetric, industryTourismMetric,
                    collector, maxToolCalls, request.industry());
            try {
                reply = llm.ask(request.sessionId(), systemPrompt, userMessage(request), tools, attempt);
                break;
            } catch (LlmUnavailableException e) {
                // 할당량 초과(429)나 과부하(503)는 다음 시도에서 예비 모델로 넘어간다.
                // 시간 초과는 이미 오래 기다렸으니 다시 부르지 않는다.
                // 화면은 60초를 기다린다. 이미 오래 걸렸으면 다시 불러도 그 안에 끝나지 않는다.
                boolean late = System.currentTimeMillis() - start > RETRY_DEADLINE_MS;
                if (++attempt >= llm.maxAttempts() || late
                        || e.getCause() instanceof java.util.concurrent.TimeoutException) {
                    throw e;
                }
                log.warn("LLM 실패, 다시 시도 ({}/{}): {}", attempt + 1, llm.maxAttempts(),
                        e.getCause() == null ? e.getMessage() : e.getCause().toString());
            }
        }
        AgentAnswer response = assemble(reply, collector, myStore(request), request.message().contains("매출"));
        onEvidence.accept(collector.snapshot(myStore(request)));

        log.info("chat session={} type={} tools={} ms={}", request.sessionId(), response.type(),
                collector.evidence().stream().map(e -> e.tool()).toList(), System.currentTimeMillis() - start);
        return response;
    }

    @Override
    public void endSession(String sessionId) {
        llm.clear(sessionId);
    }

    AgentAnswer assemble(AgentReply reply, EvidenceCollector collector, MyStoreInput myStore, boolean salesQuestion) {
        String type = reply.type() != null && TYPES.contains(reply.type()) ? reply.type() : "ANSWER";
        List<AgentAnswer.Section> sections = orEmpty(reply.sections()).stream()
                .map(s -> new AgentAnswer.Section(s.id(), s.title(), s.text()))
                .toList();
        List<String> suggestions = orEmpty(reply.suggestions()).stream().limit(3).toList();

        if (!"ANSWER".equals(type)) {
            return new AgentAnswer(type, reply.summary(), "ASK_BACK".equals(type) ? List.of() : sections,
                    List.of(), List.of(), List.of(), suggestions, "ASK_BACK".equals(type) ? askFor(reply.askFor()) : null);
        }

        List<String> chartIds = orEmpty(reply.chartIds()).stream().distinct().toList();
        // 월매출을 넣고 매출을 물었는데 LLM이 내 가게 차트를 빠뜨리면, 사용자가 넣은 숫자가 화면에 안 보이므로 맨 앞에 넣는다.
        if (salesQuestion && myStore != null && collector.snapshot(myStore).areaCustomers() != null
                && !chartIds.contains("myStoreVsArea")) {
            chartIds = Stream.concat(Stream.of("myStoreVsArea"), chartIds.stream()).toList();
        }
        chartIds = chartIds.stream().limit(MAX_CHARTS).toList();
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

    String userMessage(AgentRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("[조건]\n");
        sb.append("- 사용자 유형: ").append("EXISTING".equals(request.userType()) ? "기존 사업자" : "예비 창업자").append('\n');
        String areaName = areaNames.apply(request.area());
        if (areaName != null) {
            sb.append("- 지역: ").append(areaName).append(" (도구에 넘길 행정동 코드: ").append(request.area()).append(")\n");
        } else {
            sb.append("- 지역(행정동 코드): ").append(request.area()).append('\n');
        }
        String industryName = InputGuard.INDUSTRY_NAMES.get(request.industry());
        if (industryName != null) {
            sb.append("- 업종: ").append(industryName).append(" (도구에 넘길 업종 코드: ").append(request.industry())
                    .append(". 답변에는 코드 대신 업종 이름을 쓴다)\n");
        } else {
            sb.append("- 업종 코드: ").append(request.industry()).append('\n');
        }
        if (!request.monthlySales().isEmpty()) {
            sb.append("- 사용자가 입력한 월매출(원): ");
            request.monthlySales().forEach(s -> sb.append(s.month()).append('=').append(s.amount()).append(' '));
            if (hasSameMonthLastYear(request.monthlySales())) {
                sb.append("\n  (이 숫자와 동네 결제 전년 대비를 비교할 때는 myStoreVsArea 차트를 고르고, 비율은 직접 계산하지 말고 '줄었다/늘었다'로만 말한다)\n");
            } else {
                sb.append("\n  (작년 같은 달 매출이 없어 내 가게 전년 대비는 알 수 없다. 비교를 물으면 myStoreVsArea(내 월매출 추이)와 areaMonthly(동네 월별 결제)를 함께 고르고,")
                        .append(" 매출이 오르내린 달을 동네 흐름과 말로만 견준다. 전년 대비를 보려면 작년 같은 달 매출도 넣어 달라고 advice에 한 줄 쓴다)\n");
            }
        }
        sb.append("\n[질문]\n").append(request.message());
        // 작은 모델은 JSON 형식 지시를 보고 도구 없이 바로 답하는 경향이 있어 순서를 마지막에 다시 못박는다.
        sb.append("\n\n[순서]\n1. 데이터로 답할 수 있는 질문이면 JSON을 쓰기 전에 필요한 도구를 먼저 호출한다.")
                .append("\n2. 도구 결과를 받은 뒤에만 숫자를 쓴다. 도구를 부르지 않았으면 숫자를 하나도 쓰지 않는다.")
                .append(" 숫자끼리 더하거나 빼서 새 숫자를 만들지 않는다 (관광객 합계는 touristShare를 그대로 쓴다).")
                .append("\n3. 데이터에 없는 질문이면 도구 없이 REFUSE로 답한다.");
        return sb.toString();
    }

    /** 같은 달의 작년 매출이 함께 있어야 차트가 내 가게 전년 대비를 계산한다. */
    static boolean hasSameMonthLastYear(List<AgentRequest.MonthlySale> sales) {
        Set<String> months = sales.stream().map(AgentRequest.MonthlySale::month).collect(Collectors.toSet());
        return months.stream().anyMatch(m -> months.contains(YearMonth.parse(m).minusYears(1).toString()));
    }

    /** 화면은 정해진 네 값만 받는다. LLM이 다른 값을 쓰면 질문을 다시 써 달라는 MESSAGE로 본다. */
    static String askFor(String value) {
        return value != null && ASK_FOR.contains(value) ? value : "MESSAGE";
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}

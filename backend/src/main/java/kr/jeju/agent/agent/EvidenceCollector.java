package kr.jeju.agent.agent;

import java.util.ArrayList;
import java.util.List;

import kr.jeju.agent.chart.EvidenceSnapshot;
import kr.jeju.agent.chart.MyStoreInput;
import kr.jeju.agent.metric.AreaCustomerResult;
import kr.jeju.agent.metric.CompetitionResult;
import kr.jeju.agent.metric.IndustryTourismResult;
import kr.jeju.agent.metric.MetricMeta;

/**
 * 요청 하나 동안 실제로 호출된 도구 결과를 순서대로 모은다. 요청마다 새로 만든다.
 * 차트는 도구별 가장 최근 결과로 만든다 (두 동네를 비교하면 마지막 동네 기준).
 */
public class EvidenceCollector {

    private CompetitionResult competition;
    private AreaCustomerResult areaCustomers;
    private IndustryTourismResult industryTourism;
    private final List<AgentAnswer.Evidence> evidence = new ArrayList<>();
    private final List<MetricMeta> metas = new ArrayList<>();

    void add(CompetitionResult r) {
        competition = r;
        record("competition", r.meta());
    }

    void add(AreaCustomerResult r) {
        areaCustomers = r;
        record("areaCustomers", r.meta());
    }

    void add(IndustryTourismResult r) {
        industryTourism = r;
        record("industryTourism", r.meta());
    }

    private void record(String tool, MetricMeta meta) {
        metas.add(meta);
        evidence.add(new AgentAnswer.Evidence(tool, meta.source(), meta.period(), meta.scope(), meta.lowSample()));
    }

    public int callCount() {
        return evidence.size();
    }

    public List<AgentAnswer.Evidence> evidence() {
        return List.copyOf(evidence);
    }

    public List<MetricMeta> metas() {
        return List.copyOf(metas);
    }

    public EvidenceSnapshot snapshot(MyStoreInput myStore) {
        return new EvidenceSnapshot(competition, areaCustomers, industryTourism, myStore);
    }
}

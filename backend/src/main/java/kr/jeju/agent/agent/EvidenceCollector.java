package kr.jeju.agent.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import kr.jeju.agent.chart.EvidenceSnapshot;
import kr.jeju.agent.chart.EvidenceSnapshot.NamedAreaCustomers;
import kr.jeju.agent.chart.MyStoreInput;
import kr.jeju.agent.metric.AreaCustomerResult;
import kr.jeju.agent.metric.CompetitionResult;
import kr.jeju.agent.metric.IndustryTourismResult;
import kr.jeju.agent.metric.MetricMeta;

/**
 * 요청 하나 동안 실제로 호출된 도구 결과를 순서대로 모은다. 요청마다 새로 만든다.
 * 차트는 도구별 가장 최근 결과로 만들고, 두 동네 이상의 손님 결과가 있으면 동네별로 나란히 비교한다.
 */
public class EvidenceCollector {

    private CompetitionResult competition;
    private AreaCustomerResult areaCustomers;
    private IndustryTourismResult industryTourism;
    /** 동네 코드별 가장 최근 손님 결과 (처음 조회한 순서 유지). */
    private final Map<String, AreaCustomerResult> areaCustomersByArea = new LinkedHashMap<>();
    private final List<AgentAnswer.Evidence> evidence = new ArrayList<>();
    private final List<MetricMeta> metas = new ArrayList<>();

    void add(CompetitionResult r) {
        competition = r;
        record("competition", r.meta());
    }

    void add(AreaCustomerResult r) {
        areaCustomers = r;
        areaCustomersByArea.put(r.area(), r);
        record("areaCustomers", r.meta());
    }

    void add(IndustryTourismResult r) {
        industryTourism = r;
        record("industryTourism", r.meta());
    }

    private void record(String tool, MetricMeta meta) {
        metas.add(meta);
        // 점포 수(상가정보)는 기간이 아니라 한 시점 자료라 period가 없다. 화면은 기간을 문자열로 받으므로 채워 보낸다.
        String period = meta.period() != null ? meta.period() : "한 시점 기준";
        evidence.add(new AgentAnswer.Evidence(tool, meta.source(), period, meta.scope(), meta.lowSample()));
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
        return snapshot(myStore, code -> null);
    }

    /** areaNames: 행정동 코드 → 화면 이름. 모르면 null을 돌려주고, 그때는 코드를 그대로 범례에 쓴다. */
    public EvidenceSnapshot snapshot(MyStoreInput myStore, Function<String, String> areaNames) {
        List<NamedAreaCustomers> compared = areaCustomersByArea.size() < 2 ? List.of()
                : areaCustomersByArea.values().stream()
                        .map(r -> {
                            String name = areaNames.apply(r.area());
                            return new NamedAreaCustomers(name != null ? name : r.area(), r);
                        })
                        .toList();
        return new EvidenceSnapshot(competition, areaCustomers, industryTourism, myStore, compared);
    }
}

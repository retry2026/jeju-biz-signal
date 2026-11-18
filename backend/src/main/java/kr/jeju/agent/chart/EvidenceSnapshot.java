package kr.jeju.agent.chart;

import java.util.List;

import kr.jeju.agent.metric.AreaCustomerResult;
import kr.jeju.agent.metric.CompetitionResult;
import kr.jeju.agent.metric.IndustryTourismResult;

/**
 * @param comparedAreas 두 동네 이상을 조회했을 때 동네별 손님 결과(조회 순서). 한 동네면 빈 목록이다.
 */
public record EvidenceSnapshot(
        CompetitionResult competition,
        AreaCustomerResult areaCustomers,
        IndustryTourismResult industryTourism,
        MyStoreInput myStore,
        List<NamedAreaCustomers> comparedAreas
) {

    public EvidenceSnapshot(CompetitionResult competition, AreaCustomerResult areaCustomers,
            IndustryTourismResult industryTourism, MyStoreInput myStore) {
        this(competition, areaCustomers, industryTourism, myStore, List.of());
    }

    /** 차트 범례에 쓸 동네 이름과 그 동네의 손님 결과. */
    public record NamedAreaCustomers(String name, AreaCustomerResult result) {}
}

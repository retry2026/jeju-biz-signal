package kr.jeju.agent.chart;

import kr.jeju.agent.metric.AreaCustomerResult;
import kr.jeju.agent.metric.CompetitionResult;
import kr.jeju.agent.metric.IndustryTourismResult;

public record EvidenceSnapshot(
        CompetitionResult competition,
        AreaCustomerResult areaCustomers,
        IndustryTourismResult industryTourism,
        MyStoreInput myStore
) {}

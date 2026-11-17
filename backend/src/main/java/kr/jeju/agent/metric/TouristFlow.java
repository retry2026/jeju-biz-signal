package kr.jeju.agent.metric;

import java.util.List;

public record TouristFlow(
        String period,
        double domesticYoy,
        double foreignYoy,
        List<SeriesPoint> thisYear,
        List<SeriesPoint> lastYear
) {}

package kr.jeju.agent.chart;

import java.util.List;

public record Chart(
        String id,
        String type,
        String title,
        List<String> labels,
        List<ChartDataset> datasets,
        String unit,
        String source,
        String scope
) {}

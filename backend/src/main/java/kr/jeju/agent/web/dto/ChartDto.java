package kr.jeju.agent.web.dto;

import java.util.List;

public record ChartDto(
        String id,
        String type,
        String title,
        List<String> labels,
        List<ChartDatasetDto> datasets,
        String unit,
        String source,
        String scope) {
}

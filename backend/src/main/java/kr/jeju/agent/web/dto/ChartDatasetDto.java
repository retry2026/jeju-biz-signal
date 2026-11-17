package kr.jeju.agent.web.dto;

import java.util.List;

public record ChartDatasetDto(
        String label,
        List<Number> data,
        Boolean dashed) {
}

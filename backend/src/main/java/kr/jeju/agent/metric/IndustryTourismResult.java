package kr.jeju.agent.metric;

import java.util.List;
import java.util.Map;

public record IndustryTourismResult(
        String industry,
        String period,
        double residentShare,
        double domesticShare,
        double foreignShare,
        double touristShare,
        Map<String, Double> foreignByCountry,
        List<MonthlyPoint> seasonIndex,
        Double ticketYoy,
        MetricMeta meta
) {}

package kr.jeju.agent.metric;

public record CompetitionResult(
        String area,
        String industry,
        int storeCount,
        double jejuAvg,
        double densityRatio,
        Integer radiusM,
        Integer radiusCount,
        MetricMeta meta
) {}

package kr.jeju.agent.metric;

public record MetricMeta(
        String source,
        String period,
        String scope,
        boolean lowSample,
        String note
) {}

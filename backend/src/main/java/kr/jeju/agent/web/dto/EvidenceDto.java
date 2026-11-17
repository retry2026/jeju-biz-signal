package kr.jeju.agent.web.dto;

public record EvidenceDto(
        String tool,
        String source,
        String period,
        String scope,
        boolean lowSample) {
}

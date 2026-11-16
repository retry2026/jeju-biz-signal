package kr.jeju.agent.web.dto;

import java.util.List;

public record ChatResponse(
        String type,
        String summary,
        List<SectionDto> sections,
        List<ChartDto> charts,
        List<EvidenceDto> evidence,
        List<String> limits,
        List<String> suggestions,
        String askFor) {
}

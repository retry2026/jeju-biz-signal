package kr.jeju.agent.web.dto;

import java.util.List;
import java.util.Map;

public record MetaResponse(
        List<AreaDto> areas,
        List<IndustryDto> industries,
        Map<String, List<String>> exampleQuestions,
        DataAsOfDto dataAsOf) {
}

package kr.jeju.agent.agent;

import java.util.List;

import kr.jeju.agent.chart.Chart;

/**
 * 에이전트 출력. web의 응답 DTO와 독립된 ③의 타입이며 필드 구성은 API명세 3장 응답과 같다.
 *
 * @param type   ANSWER, REFUSE, ASK_BACK
 * @param askFor ASK_BACK일 때 AREA, INDUSTRY, MESSAGE, MY_STORE. 그 외 null
 */
public record AgentAnswer(
        String type,
        String summary,
        List<Section> sections,
        List<Chart> charts,
        List<Evidence> evidence,
        List<String> limits,
        List<String> suggestions,
        String askFor) {

    public record Section(String id, String title, String text) {
    }

    /** @param scope AREA, RADIUS, JEJU */
    public record Evidence(String tool, String source, String period, String scope, boolean lowSample) {
    }
}

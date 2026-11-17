package kr.jeju.agent.agent;

import java.util.List;

/**
 * LLM 구조화 출력. 숫자 필드가 없다. 숫자, 차트, 근거, 한계는 코드가 도구 결과로 만든다.
 *
 * @param type     ANSWER, REFUSE, ASK_BACK
 * @param chartIds 보여줄 차트 ID (최대 3개)
 * @param askFor   ASK_BACK일 때 AREA, INDUSTRY, MESSAGE, MY_STORE
 */
public record AgentReply(
        String type,
        String summary,
        List<SectionText> sections,
        List<String> chartIds,
        List<String> suggestions,
        String askFor) {

    public record SectionText(String id, String title, String text) {
    }
}

package kr.jeju.agent.agent;

import java.util.List;
import java.util.Optional;


/** LLM을 부르기 전에 코드로 되묻기를 결정한다. 기본값을 넣지 않는다 (FR-10, FR-17). */
public class InputGuard {

    private static final List<String> MY_STORE_KEYWORDS =
            List.of("우리 가게", "내 가게", "작년보다", "전년보다", "매출이 줄", "매출이 떨어", "매출 하락", "가게 문제");

    public Optional<AgentAnswer> check(AgentRequest request) {
        if (isBlank(request.area())) {
            return Optional.of(askBack("어느 동네인지 먼저 골라 주세요.", "AREA"));
        }
        if (isBlank(request.industry())) {
            return Optional.of(askBack("어떤 업종인지 먼저 골라 주세요.", "INDUSTRY"));
        }
        if (isBlank(request.message())) {
            return Optional.of(askBack("궁금한 점을 질문으로 적어 주세요.", "MESSAGE"));
        }
        if (needsMyStore(request)) {
            return Optional.of(askBack(
                    "작년과 비교하려면 최근 월매출과 작년 같은 달 매출을 입력해 주세요.", "MY_STORE"));
        }
        return Optional.empty();
    }

    private boolean needsMyStore(AgentRequest request) {
        return "EXISTING".equals(request.userType()) && request.monthlySales().isEmpty()
                && MY_STORE_KEYWORDS.stream().anyMatch(request.message()::contains);
    }

    static AgentAnswer askBack(String summary, String askFor) {
        return new AgentAnswer("ASK_BACK", summary, List.of(), List.of(), List.of(), List.of(), List.of(), askFor);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

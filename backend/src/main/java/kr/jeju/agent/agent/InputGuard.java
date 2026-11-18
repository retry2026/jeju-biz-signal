package kr.jeju.agent.agent;

import java.util.List;
import java.util.Map;
import java.util.Optional;


/** LLM을 부르기 전에 코드로 되묻기를 결정한다. 기본값을 넣지 않는다 (FR-10, FR-17). */
public class InputGuard {

    /**
     * 내 가게 매출을 동네와 비교해 달라는 표현. "작년보다", "매출이 줄" 같은 말은 관광객 추이나
     * 계절 질문에도 쓰여서 넣지 않는다 (예: "겨울만 되면 매출이 줄어요"는 월매출 없이 답할 수 있다).
     */
    private static final List<String> MY_STORE_KEYWORDS =
            List.of("우리 가게", "내 가게", "우리 매출", "내 매출", "가게 문제", "우리 집 매출");

    /** 업종 코드별 화면 이름과, 질문에서 그 업종을 가리키는 말. 업종은 화면에서 고른 것 하나로만 답한다. */
    private static final Map<String, String> INDUSTRY_NAMES = Map.of(
            "CAFE", "카페", "KOREAN", "한식", "CHINESE", "중식", "JAPANESE", "일식",
            "WESTERN", "양식", "PUB", "주점", "FASTFOOD", "패스트푸드");
    private static final Map<String, List<String>> INDUSTRY_WORDS = Map.of(
            "CAFE", List.of("카페", "커피"),
            "KOREAN", List.of("한식", "고깃집", "고기집"),
            "CHINESE", List.of("중식", "중국집"),
            "JAPANESE", List.of("일식", "초밥", "스시"),
            "WESTERN", List.of("양식", "파스타"),
            "PUB", List.of("주점", "술집", "호프", "포차"),
            "FASTFOOD", List.of("패스트푸드", "햄버거", "버거", "피자"));

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
        Optional<String> other = otherIndustry(request);
        if (other.isPresent()) {
            String selected = INDUSTRY_NAMES.getOrDefault(request.industry(), request.industry());
            return Optional.of(askBack("지금 고른 업종은 " + selected + "입니다. " + other.get()
                    + " 이야기가 궁금하면 업종을 " + withRo(other.get()) + " 바꿔 주세요.", "INDUSTRY"));
        }
        if (needsMyStore(request)) {
            return Optional.of(askBack(
                    "작년과 비교하려면 최근 월매출과 작년 같은 달 매출을 입력해 주세요.", "MY_STORE"));
        }
        return Optional.empty();
    }

    /** 질문이 고른 업종은 말하지 않고 다른 업종만 말하면 그 업종 이름을 돌려준다. */
    static Optional<String> otherIndustry(AgentRequest request) {
        String message = request.message();
        boolean mentionsSelected = INDUSTRY_WORDS.getOrDefault(request.industry(), List.of()).stream()
                .anyMatch(message::contains);
        if (mentionsSelected) {
            return Optional.empty();
        }
        return INDUSTRY_WORDS.entrySet().stream()
                .filter(e -> !e.getKey().equals(request.industry()))
                .filter(e -> e.getValue().stream().anyMatch(message::contains))
                .map(e -> INDUSTRY_NAMES.get(e.getKey()))
                .sorted()
                .findFirst();
    }

    /** 받침이 있으면 "으로", 없거나 ㄹ 받침이면 "로" (카페로, 주점으로). */
    static String withRo(String word) {
        char last = word.charAt(word.length() - 1);
        int jong = last >= 0xAC00 && last <= 0xD7A3 ? (last - 0xAC00) % 28 : 0;
        return word + (jong == 0 || jong == 8 ? "로" : "으로");
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

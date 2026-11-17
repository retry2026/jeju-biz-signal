package kr.jeju.agent.eval;

import java.util.List;

import kr.jeju.agent.agent.AgentRequest;

/**
 * 평가 질문 하나 (eval/questions.yaml의 한 항목, API명세 7장).
 *
 * @param scenario A, B, C, REFUSE, ASK_BACK
 */
public record EvalQuestion(String id, String scenario, AgentRequest request, Expect expect) {

    /**
     * @param type    기대 응답 유형 ANSWER, REFUSE, ASK_BACK
     * @param tools   반드시 호출돼야 하는 도구
     * @param numbers 답에 나와야 하는 숫자 (정답 SQL로 계산)
     */
    public record Expect(String type, List<String> tools, List<NumberCheck> numbers) {

        public Expect {
            tools = tools == null ? List.of() : List.copyOf(tools);
            numbers = numbers == null ? List.of() : List.copyOf(numbers);
        }
    }

    /** @param gold eval/gold 아래 SQL 파일 이름. 첫 행 첫 열 숫자가 정답, @param tolerance 허용 오차 (단위 그대로) */
    public record NumberCheck(String gold, double tolerance) {
    }
}

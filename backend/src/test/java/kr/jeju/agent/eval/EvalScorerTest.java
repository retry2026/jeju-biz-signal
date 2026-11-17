package kr.jeju.agent.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kr.jeju.agent.agent.AgentAnswer;
import kr.jeju.agent.agent.AgentRequest;
import kr.jeju.agent.chart.Chart;
import kr.jeju.agent.chart.ChartDataset;
import kr.jeju.agent.eval.EvalScorer.QuestionScore;

class EvalScorerTest {

    final EvalScorer scorer = new EvalScorer();

    static EvalQuestion question(String type, List<String> tools, List<EvalQuestion.NumberCheck> numbers) {
        AgentRequest req = new AgentRequest("s", "EXISTING", "50110250", "CAFE", null, "겨울만 되면 매출이 반토막 나요");
        return new EvalQuestion("Q01", "B", req, new EvalQuestion.Expect(type, tools, numbers));
    }

    static AgentAnswer answer(String type, String text, List<String> tools, List<Double> chartData) {
        return new AgentAnswer(type, "요약", List.of(new AgentAnswer.Section("customers", "동네 손님", text)),
                List.of(new Chart("customerMix", "doughnut", "t", List.of("a", "b", "c"),
                        List.of(new ChartDataset("x", chartData, null)), "%", "s", "AREA")),
                tools.stream().map(t -> new AgentAnswer.Evidence(t, "s", "p", "AREA", false)).toList(),
                List.of(), List.of(), null);
    }

    @Test
    void 유형_도구_정답숫자가_맞으면_모두_통과한다() {
        QuestionScore s = scorer.score(
                question("ANSWER", List.of("areaCustomers"), List.of(new EvalQuestion.NumberCheck("Q01.sql", 0.5))),
                answer("ANSWER", "애월 외식 결제의 58.4%가 내국인 관광객입니다.", List.of("areaCustomers", "industryTourism"),
                        List.of(31.2, 58.4, 10.4)),
                Map.of("Q01.sql", 58.2));

        assertThat(s.typeOk()).isTrue();
        assertThat(s.toolsOk()).isTrue();
        assertThat(s.numbers()).allMatch(EvalScorer.NumberResult::found);
        assertThat(s.unsupported()).isEmpty();
    }

    @Test
    void 기대_도구를_안_부르면_도구_실패() {
        QuestionScore s = scorer.score(question("ANSWER", List.of("areaCustomers", "industryTourism"), null),
                answer("ANSWER", "본문", List.of("areaCustomers"), List.of(1.0, 2.0, 3.0)), Map.of());

        assertThat(s.toolsOk()).isFalse();
    }

    @Test
    void 정답과_허용오차_밖이면_숫자_실패() {
        QuestionScore s = scorer.score(
                question("ANSWER", List.of(), List.of(new EvalQuestion.NumberCheck("Q01.sql", 0.5))),
                answer("ANSWER", "관광객 비중은 61.0%입니다.", List.of(), List.of(61.0)), Map.of("Q01.sql", 58.4));

        assertThat(s.numbers()).noneMatch(EvalScorer.NumberResult::found);
    }

    @Test
    void 어디에도_없는_숫자는_근거_없는_숫자로_센다() {
        QuestionScore s = scorer.score(question("ANSWER", List.of(), List.of()),
                answer("ANSWER", "2026년 기준 12개월 동안 관광객 비중은 58.4%이고 매출은 37.9% 늘 것입니다.",
                        List.of(), List.of(58.4)), Map.of());

        assertThat(s.unsupported()).containsExactly(37.9);
        assertThat(s.numberCount()).isEqualTo(2);
    }

    @Test
    void 감소를_말로_쓰면_부호_없는_숫자도_정답으로_본다() {
        QuestionScore s = scorer.score(
                question("ANSWER", List.of(), List.of(new EvalQuestion.NumberCheck("Q01.sql", 0.5))),
                answer("ANSWER", "동네 외식 결제는 전년보다 3.5% 줄었습니다.", List.of(), List.of()), Map.of("Q01.sql", -3.5));

        assertThat(s.numbers()).allMatch(EvalScorer.NumberResult::found);
        assertThat(s.unsupported()).isEmpty();
    }

    @Test
    void 기간_표기와_계절_지수_기준값은_근거_없는_숫자로_세지_않는다() {
        QuestionScore s = scorer.score(question("ANSWER", List.of(), List.of()),
                answer("ANSWER", "2025-09 ~ 2026-09 기준, 2026-10-16부터 4주. 연평균 100 기준 8월 지수는 107.7입니다.",
                        List.of(), List.of(107.7)), Map.of());

        assertThat(s.unsupported()).isEmpty();
        assertThat(EvalScorer.numbers("2025-09 ~ 2026-09")).isEmpty();
    }

    @Test
    void 쉼표가_있는_금액도_숫자로_읽는다() {
        assertThat(EvalScorer.numbers("건당 14,200원, -6.3%")).containsExactly(14200.0, -6.3);
    }

    @Test
    void 거절_기대인데_답하면_유형_실패() {
        QuestionScore s = scorer.score(question("REFUSE", List.of(), List.of()),
                answer("ANSWER", "저녁 매출은 좋습니다.", List.of(), List.of()), Map.of());

        assertThat(s.typeOk()).isFalse();
    }
}

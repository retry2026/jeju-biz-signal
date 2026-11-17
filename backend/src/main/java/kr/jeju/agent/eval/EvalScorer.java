package kr.jeju.agent.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kr.jeju.agent.agent.AgentAnswer;
import kr.jeju.agent.agent.AgentRequest;
import kr.jeju.agent.chart.Chart;
import kr.jeju.agent.chart.ChartDataset;

/**
 * 응답 하나를 채점한다. LLM 없이 테스트할 수 있도록 순수 계산만 한다.
 *
 * 근거 없는 숫자: 본문 숫자 중 도구 결과, 차트 데이터, 정답 값, 질문과 입력값 어디에서도 찾을 수 없는 숫자.
 * 연도(1900~2100)와 1~12 사이 정수(개월, 주 등)는 세지 않는다. 근사치라 사람이 결과를 한 번 더 본다.
 */
public class EvalScorer {

    private static final Pattern NUMBER = Pattern.compile("-?\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|-?\\d+(?:\\.\\d+)?");

    public QuestionScore score(EvalQuestion q, AgentAnswer answer, Map<String, Double> goldValues) {
        return score(q, answer, goldValues, List.of());
    }

    /** @param toolNumbers 이번 요청에서 부른 도구 결과에 들어 있던 숫자. 본문 숫자의 근거로 인정한다. */
    public QuestionScore score(EvalQuestion q, AgentAnswer answer, Map<String, Double> goldValues,
            List<Double> toolNumbers) {
        boolean typeOk = Objects.equals(q.expect().type(), answer.type());

        Boolean toolsOk = null;
        if (!q.expect().tools().isEmpty()) {
            List<String> called = answer.evidence().stream().map(AgentAnswer.Evidence::tool).toList();
            toolsOk = called.containsAll(q.expect().tools());
        }

        List<Double> textNumbers = numbers(text(answer));
        List<NumberResult> numberResults = new ArrayList<>();
        for (EvalQuestion.NumberCheck check : q.expect().numbers()) {
            Double gold = goldValues.get(check.gold());
            boolean found = gold != null && textNumbers.stream().anyMatch(n -> Math.abs(n - gold) <= check.tolerance());
            numberResults.add(new NumberResult(check.gold(), gold, found));
        }

        List<Double> allowed = allowedNumbers(q.request(), answer, goldValues);
        allowed.addAll(toolNumbers);
        List<Double> unsupported = textNumbers.stream()
                .filter(n -> !ignorable(n))
                .filter(n -> allowed.stream().noneMatch(a -> close(a, n)))
                .toList();

        return new QuestionScore(q.id(), q.scenario(), q.expect().type(), answer.type(), typeOk, toolsOk,
                numberResults, (int) textNumbers.stream().filter(n -> !ignorable(n)).count(), unsupported);
    }

    static String text(AgentAnswer answer) {
        StringBuilder sb = new StringBuilder(Objects.toString(answer.summary(), ""));
        answer.sections().forEach(s -> sb.append('\n').append(Objects.toString(s.text(), "")));
        return sb.toString();
    }

    static List<Double> numbers(String text) {
        List<Double> numbers = new ArrayList<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            numbers.add(Double.parseDouble(m.group().replace(",", "")));
        }
        return numbers;
    }

    private static List<Double> allowedNumbers(AgentRequest request, AgentAnswer answer, Map<String, Double> gold) {
        List<Double> allowed = new ArrayList<>(gold.values());
        for (Chart chart : answer.charts()) {
            for (ChartDataset ds : chart.datasets()) {
                ds.data().stream().filter(Objects::nonNull).forEach(allowed::add);
            }
        }
        allowed.addAll(numbers(Objects.toString(request.message(), "")));
        for (AgentRequest.MonthlySale sale : request.monthlySales()) {
            if (sale.amount() != null) {
                allowed.add(sale.amount().doubleValue());
                allowed.add(sale.amount() / 10_000.0); // 만원 단위로 말하는 경우
            }
        }
        return allowed;
    }

    private static boolean ignorable(double n) {
        boolean integer = n == Math.rint(n);
        return integer && ((n >= 1900 && n <= 2100) || (n >= 0 && n <= 12));
    }

    /** 반올림, 단위 표기 차이를 감안해 0.1 또는 1% 안이면 같은 숫자로 본다. */
    private static boolean close(double a, double b) {
        return Math.abs(a - b) <= Math.max(0.1, Math.abs(a) * 0.01);
    }

    public record NumberResult(String gold, Double goldValue, boolean found) {
    }

    /**
     * @param toolsOk     기대 도구가 없으면 null
     * @param numberCount 본문의 채점 대상 숫자 수
     * @param unsupported 근거를 찾지 못한 숫자
     */
    public record QuestionScore(String id, String scenario, String expectedType, String actualType, boolean typeOk,
            Boolean toolsOk, List<NumberResult> numbers, int numberCount, List<Double> unsupported) {
    }
}

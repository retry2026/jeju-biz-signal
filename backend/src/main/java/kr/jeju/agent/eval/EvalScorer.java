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

    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}(?:-\\d{2})?");
    private static final Pattern KOREAN_DATE = Pattern.compile("(?:\\d{4}년\\s*)?\\d{1,2}월\\s*\\d{1,2}일|\\d{4}년\\s*\\d{1,2}월");
    private static final Pattern NUMBER = Pattern.compile("-?\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|-?\\d+(?:\\.\\d+)?");
    /** 억·만 단위를 섞은 금액 (만은 반드시 있어야 함). 예: "3만 2,701원", "1억 2,000만 원", "1,500만원" */
    private static final Pattern KOREAN_AMOUNT = Pattern.compile(
            "(?:(\\d[\\d,]*)\\s*억\\s*)?(\\d[\\d,]*)\\s*만\\s*(\\d[\\d,]*(?=\\s*원))?");

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
            // "3.5% 줄었다"처럼 부호를 말로 쓰는 경우가 많아 절댓값으로도 비교한다.
            boolean found = gold != null && textNumbers.stream().anyMatch(n -> Math.abs(n - gold) <= check.tolerance()
                    || Math.abs(Math.abs(n) - Math.abs(gold)) <= check.tolerance());
            numberResults.add(new NumberResult(check.gold(), gold, found));
        }

        List<Double> allowed = allowedNumbers(q.request(), answer, goldValues);
        allowed.addAll(toolNumbers);
        List<Double> unsupported = textNumbers.stream()
                .filter(n -> !ignorable(n))
                .filter(n -> allowed.stream().noneMatch(a -> close(a, n) || close(Math.abs(a), Math.abs(n))))
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
        // "2025-09", "2026-10-16" 같은 기간 표기는 숫자로 세지 않는다 (-09를 음수로 읽는 오탐 방지).
        // "2026년 10월 20일"처럼 한글로 쓴 날짜도 숫자로 세지 않는다.
        String noDates = KOREAN_DATE.matcher(DATE.matcher(text).replaceAll(" ")).replaceAll(" ");
        String cleaned = koreanAmounts(noDates);
        Matcher m = NUMBER.matcher(cleaned);
        while (m.find()) {
            numbers.add(Double.parseDouble(m.group().replace(",", "")));
        }
        return numbers;
    }

    /** "3만 2,701원", "1억 2,000만 원", "1,500만원"처럼 억·만을 섞어 쓴 금액을 숫자 하나로 바꾼다. */
    static String koreanAmounts(String text) {
        Matcher m = KOREAN_AMOUNT.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            long value = part(m.group(1)) * 100_000_000L + part(m.group(2)) * 10_000L + part(m.group(3));
            m.appendReplacement(sb, Long.toString(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static long part(String digits) {
        return digits == null ? 0 : Long.parseLong(digits.replace(",", ""));
    }

    private static List<Double> allowedNumbers(AgentRequest request, AgentAnswer answer, Map<String, Double> gold) {
        List<Double> allowed = new ArrayList<>(gold.values());
        for (Chart chart : answer.charts()) {
            for (ChartDataset ds : chart.datasets()) {
                ds.data().stream().filter(Objects::nonNull).forEach(allowed::add);
            }
        }
        allowed.addAll(numbers(Objects.toString(request.message(), "")));
        allowed.add(100.0); // 계절 지수의 기준값 "연평균 100"
        allowed.add(500.0); // 반경 설정값 "반경 500m" (계산 결과가 아니라 도구에 넘긴 값)
        allowed.add(24.0);  // 표본 부족 시 넓히는 기간 "24개월"
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

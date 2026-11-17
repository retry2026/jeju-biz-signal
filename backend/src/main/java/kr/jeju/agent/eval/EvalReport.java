package kr.jeju.agent.eval;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import kr.jeju.agent.eval.EvalScorer.NumberResult;
import kr.jeju.agent.eval.EvalService.Run;

/** 평가 결과를 마크다운으로 만든다. 결과 설명자료의 검증 표에 그대로 쓴다. */
public class EvalReport {

    public record Summary(int runs, int errors, Ratio type, Ratio tools, Ratio numbers, Ratio refuse,
            Ratio unsupported, long avgMillis) {
    }

    /** ok/total. total이 0이면 해당 없음. */
    public record Ratio(long ok, long total) {
        String text() {
            return total == 0 ? "해당 없음" : String.format("%.1f%% (%d/%d)", ok * 100.0 / total, ok, total);
        }
    }

    public Summary summarize(List<Run> runs) {
        List<Run> scored = runs.stream().filter(r -> r.score() != null).toList();
        long numbersTotal = scored.stream().mapToLong(r -> r.score().numbers().size()).sum();
        long numbersOk = scored.stream().mapToLong(r -> r.score().numbers().stream().filter(NumberResult::found).count()).sum();
        long textNumbers = scored.stream().mapToLong(r -> r.score().numberCount()).sum();
        long unsupported = scored.stream().mapToLong(r -> r.score().unsupported().size()).sum();
        return new Summary(runs.size(),
                (int) runs.stream().filter(r -> r.error() != null).count(),
                ratio(runs, r -> r.score() != null && r.score().typeOk()),
                new Ratio(scored.stream().filter(r -> Boolean.TRUE.equals(r.score().toolsOk())).count(),
                        scored.stream().filter(r -> r.score().toolsOk() != null).count()),
                new Ratio(numbersOk, numbersTotal),
                ratio(runs.stream().filter(r -> "REFUSE".equals(r.question().expect().type())).toList(),
                        r -> r.score() != null && r.score().typeOk()),
                new Ratio(unsupported, textNumbers),
                runs.isEmpty() ? 0 : (long) runs.stream().mapToLong(Run::millis).average().orElse(0));
    }

    public String markdown(List<Run> runs, String header) {
        Summary s = summarize(runs);
        StringBuilder md = new StringBuilder();
        md.append("# 에이전트 평가 결과\n\n").append(header).append("\n\n");
        md.append("| 지표 | 결과 |\n|---|---|\n");
        md.append("| 실행 수 | ").append(s.runs()).append(" (오류 ").append(s.errors()).append(") |\n");
        md.append("| 판단 정확도 (응답 유형) | ").append(s.type().text()).append(" |\n");
        md.append("| 도구 선택 정확도 | ").append(s.tools().text()).append(" |\n");
        md.append("| 수치 정확도 | ").append(s.numbers().text()).append(" |\n");
        md.append("| 거절 정확도 | ").append(s.refuse().text()).append(" |\n");
        md.append("| 근거 없는 숫자 비율 | ").append(s.unsupported().text()).append(" |\n");
        md.append("| 평균 응답 시간 | ").append(s.avgMillis()).append(" ms |\n\n");

        md.append("## 질문별\n\n| ID | 시나리오 | 회차 | 기대 | 실제 | 유형 | 도구 | 숫자 | 근거 없는 숫자 | ms | 오류 |\n");
        md.append("|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (Run r : runs) {
            var sc = r.score();
            md.append("| ").append(r.question().id()).append(" | ").append(r.question().scenario())
                    .append(" | ").append(r.attempt())
                    .append(" | ").append(r.question().expect().type())
                    .append(" | ").append(sc == null ? "-" : sc.actualType())
                    .append(" | ").append(sc == null ? "-" : mark(sc.typeOk()))
                    .append(" | ").append(sc == null || sc.toolsOk() == null ? "-" : mark(sc.toolsOk()))
                    .append(" | ").append(sc == null || sc.numbers().isEmpty() ? "-"
                            : sc.numbers().stream().filter(NumberResult::found).count() + "/" + sc.numbers().size())
                    .append(" | ").append(sc == null || sc.unsupported().isEmpty() ? "-" : sc.unsupported().toString())
                    .append(" | ").append(r.millis())
                    .append(" | ").append(r.error() == null ? "" : r.error().replace("|", "/"))
                    .append(" |\n");
        }

        md.append("\n## 답변 원문\n");
        for (Run r : runs) {
            if (r.answer() == null) {
                continue;
            }
            md.append("\n### ").append(r.question().id()).append(" (").append(r.attempt()).append("회차)\n\n");
            md.append("- 질문: ").append(r.question().request().message()).append('\n');
            md.append("- 결론: ").append(Objects.toString(r.answer().summary(), "")).append('\n');
            r.answer().sections().forEach(sec -> md.append("- ").append(sec.title()).append(": ").append(sec.text()).append('\n'));
        }
        return md.toString();
    }

    private static Ratio ratio(List<Run> runs, Predicate<Run> ok) {
        return new Ratio(runs.stream().filter(ok).count(), runs.size());
    }

    private static String mark(boolean ok) {
        return ok ? "O" : "X";
    }
}

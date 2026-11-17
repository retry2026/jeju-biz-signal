package kr.jeju.agent.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import tools.jackson.databind.json.JsonMapper;

import kr.jeju.agent.agent.AgentAnswer;
import kr.jeju.agent.agent.AgentService;
import kr.jeju.agent.agent.ChatAgent;
import kr.jeju.agent.agent.AgentRequest;
import kr.jeju.agent.eval.EvalScorer.QuestionScore;

/**
 * 질문마다 에이전트를 repeat번 부르고 채점한다. 정답 값은 goldLookup(SQL 파일 이름 → 숫자)으로 받는다.
 * LLM 오류나 예외는 실패한 실행으로 기록하고 다음 질문으로 넘어간다.
 */
public class EvalService {

    private final ChatAgent agent;
    private final Function<String, Double> goldLookup;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final EvalScorer scorer = new EvalScorer();

    /** 실행 사이 대기(ms). 무료 LLM의 분당 요청 한도에 걸리지 않게 한다. */
    private final long delayMs;

    public EvalService(ChatAgent agent, Function<String, Double> goldLookup) {
        this(agent, goldLookup, 0);
    }

    public EvalService(ChatAgent agent, Function<String, Double> goldLookup, long delayMs) {
        this.agent = agent;
        this.goldLookup = goldLookup;
        this.delayMs = delayMs;
    }

    public List<Run> run(List<EvalQuestion> questions, int repeat) {
        List<Run> runs = new ArrayList<>();
        for (EvalQuestion q : questions) {
            Map<String, Double> gold = new LinkedHashMap<>();
            for (EvalQuestion.NumberCheck check : q.expect().numbers()) {
                gold.put(check.gold(), lookup(check.gold()));
            }
            for (int i = 1; i <= repeat; i++) {
                if (!runs.isEmpty()) {
                    pause();
                }
                runs.add(runOnce(q, i, gold));
            }
        }
        return runs;
    }

    private void pause() {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Run runOnce(EvalQuestion q, int attempt, Map<String, Double> gold) {
        AgentRequest r = q.request();
        String sessionId = r.sessionId() + "-" + attempt;
        AgentRequest request = new AgentRequest(sessionId, r.userType(), r.area(), r.industry(), r.monthlySales(), r.message());
        long start = System.currentTimeMillis();
        try {
            // 실제 에이전트면 이번 요청의 도구 결과 숫자도 근거로 인정한다 (차트에 안 고른 값을 본문에 쓴 경우).
            List<Double> toolNumbers = new ArrayList<>();
            AgentAnswer answer = agent instanceof AgentService svc
                    ? svc.answer(request, s -> toolNumbers.addAll(EvalScorer.numbers(JSON.writeValueAsString(s))))
                    : agent.answer(request);
            QuestionScore score = scorer.score(q, answer, gold, toolNumbers);
            return new Run(q, attempt, score, answer, null, System.currentTimeMillis() - start);
        } catch (RuntimeException e) {
            return new Run(q, attempt, null, null, e.getClass().getSimpleName() + ": " + e.getMessage(),
                    System.currentTimeMillis() - start);
        } finally {
            agent.endSession(sessionId);
        }
    }

    private Double lookup(String gold) {
        try {
            return goldLookup.apply(gold);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** score와 answer는 error가 있으면 null. */
    public record Run(EvalQuestion question, int attempt, QuestionScore score, AgentAnswer answer, String error,
            long millis) {
    }
}

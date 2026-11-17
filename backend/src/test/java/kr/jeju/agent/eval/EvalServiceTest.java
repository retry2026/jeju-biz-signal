package kr.jeju.agent.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import kr.jeju.agent.agent.AgentAnswer;
import kr.jeju.agent.agent.AgentRequest;
import kr.jeju.agent.agent.ChatAgent;
import kr.jeju.agent.agent.LlmUnavailableException;

class EvalServiceTest {

    @Test
    void 예시_질문_파일을_읽는다() throws Exception {
        List<EvalQuestion> qs = new EvalQuestionLoader().load(Path.of("../eval/questions.example.yaml"));

        assertThat(qs).extracting(EvalQuestion::id).containsExactly("EX01", "EX02", "EX03", "EX04", "EX05");
        assertThat(qs.get(0).expect().tools()).containsExactly("competition", "areaCustomers");
        assertThat(qs.get(2).request().monthlySales()).hasSize(2);
        assertThat(qs.get(3).expect().type()).isEqualTo("REFUSE");
    }

    @Test
    void 질문마다_반복_실행하고_오류도_기록하며_세션을_정리한다() throws Exception {
        List<EvalQuestion> qs = new EvalQuestionLoader().load(Path.of("../eval/questions.example.yaml"));
        List<String> ended = new ArrayList<>();
        ChatAgent agent = new ChatAgent() {
            @Override
            public AgentAnswer answer(AgentRequest r) {
                if (r.message().contains("저녁")) {
                    throw new LlmUnavailableException("테스트 오류", null);
                }
                return new AgentAnswer("ANSWER", "요약", List.of(), List.of(),
                        List.of(new AgentAnswer.Evidence("areaCustomers", "s", "p", "AREA", false)),
                        List.of(), List.of(), null);
            }

            @Override
            public void endSession(String sessionId) {
                ended.add(sessionId);
            }
        };

        List<EvalService.Run> runs = new EvalService(agent, name -> 1.0).run(qs, 2);

        assertThat(runs).hasSize(10);
        assertThat(ended).hasSize(10).doesNotHaveDuplicates();
        assertThat(runs).filteredOn(r -> r.error() != null).hasSize(2);

        EvalReport report = new EvalReport();
        EvalReport.Summary s = report.summarize(runs);
        assertThat(s.errors()).isEqualTo(2);
        assertThat(s.refuse().total()).isEqualTo(2);
        assertThat(s.refuse().ok()).isZero();
        assertThat(report.markdown(runs, "- 테스트")).contains("# 에이전트 평가 결과", "| EX02 | B |");
    }
}

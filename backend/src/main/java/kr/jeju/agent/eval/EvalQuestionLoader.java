package kr.jeju.agent.eval;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

import kr.jeju.agent.agent.AgentRequest;

/** eval/questions.yaml을 읽는다 (형식은 API명세 7장). */
public class EvalQuestionLoader {

    public List<EvalQuestion> load(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<Map<String, Object>> items = new Yaml().load(reader);
            List<EvalQuestion> questions = new ArrayList<>();
            for (Map<String, Object> item : items == null ? List.<Map<String, Object>>of() : items) {
                questions.add(toQuestion(item));
            }
            return questions;
        }
    }

    @SuppressWarnings("unchecked")
    private static EvalQuestion toQuestion(Map<String, Object> item) {
        Map<String, Object> req = (Map<String, Object>) item.get("request");
        Map<String, Object> exp = (Map<String, Object>) item.get("expect");
        String id = str(item.get("id"));

        List<AgentRequest.MonthlySale> sales = new ArrayList<>();
        Object myStore = req.get("myStore");
        if (myStore instanceof Map<?, ?> store && store.get("monthlySales") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> sale = (Map<String, Object>) o;
                sales.add(new AgentRequest.MonthlySale(str(sale.get("month")), ((Number) sale.get("amount")).longValue()));
            }
        }
        AgentRequest request = new AgentRequest("eval-" + id, str(req.get("userType")), str(req.get("area")),
                str(req.get("industry")), sales, str(req.get("message")));

        List<EvalQuestion.NumberCheck> numbers = new ArrayList<>();
        if (exp.get("numbers") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> n = (Map<String, Object>) o;
                Object tol = n.get("tolerance");
                numbers.add(new EvalQuestion.NumberCheck(str(n.get("gold")), tol == null ? 0.5 : ((Number) tol).doubleValue()));
            }
        }
        return new EvalQuestion(id, str(item.get("scenario")), request,
                new EvalQuestion.Expect(str(exp.get("type")), (List<String>) exp.get("tools"), numbers));
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}

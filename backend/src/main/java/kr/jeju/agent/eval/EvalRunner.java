package kr.jeju.agent.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import kr.jeju.agent.agent.ChatAgent;

/**
 * 평가 실행기. eval 프로필로 실행하면 질문을 모두 돌리고 eval/results에 결과를 쓴 뒤 종료한다.
 *
 * <pre>
 * cd backend
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=eval
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=eval -Dspring-boot.run.arguments="--app.eval.repeat=1"
 * </pre>
 */
@Component
@Profile("eval")
public class EvalRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

    private final ChatAgent agent;
    private final JdbcTemplate jdbc;
    private final ApplicationContext context;
    private final Path questionsFile;
    private final Path goldDir;
    private final Path resultsDir;
    private final int repeat;
    private final String model;

    public EvalRunner(ChatAgent agent, JdbcTemplate jdbc, ApplicationContext context,
            @Value("${app.eval.questions:../eval/questions.yaml}") String questionsFile,
            @Value("${app.eval.gold-dir:../eval/gold}") String goldDir,
            @Value("${app.eval.results-dir:../eval/results}") String resultsDir,
            @Value("${app.eval.repeat:3}") int repeat,
            @Value("${spring.ai.model.chat:none} / ${spring.ai.google.genai.chat.model:}") String model) {
        this.agent = agent;
        this.jdbc = jdbc;
        this.context = context;
        this.questionsFile = Path.of(questionsFile);
        this.goldDir = Path.of(goldDir);
        this.resultsDir = Path.of(resultsDir);
        this.repeat = repeat;
        this.model = model;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        List<EvalQuestion> questions = new EvalQuestionLoader().load(questionsFile);
        log.info("평가 시작: 질문 {}개 × {}회 ({})", questions.size(), repeat, questionsFile.toAbsolutePath());

        List<EvalService.Run> runs = new EvalService(agent, this::gold).run(questions, repeat);
        EvalReport report = new EvalReport();
        String header = "- 실행 시각: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                + "\n- 커밋: " + gitCommit()
                + "\n- 모델: " + model
                + "\n- 질문 파일: " + questionsFile.getFileName() + " (" + questions.size() + "개 × " + repeat + "회)";

        Files.createDirectories(resultsDir);
        Path out = resultsDir.resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")) + ".md");
        Files.writeString(out, report.markdown(runs, header), StandardCharsets.UTF_8);

        EvalReport.Summary s = report.summarize(runs);
        log.info("평가 끝: 판단 {}, 도구 {}, 수치 {}, 거절 {}, 근거 없는 숫자 {} → {}", s.type().text(), s.tools().text(),
                s.numbers().text(), s.refuse().text(), s.unsupported().text(), out.toAbsolutePath());
        System.exit(SpringApplication.exit(context, () -> 0));
    }

    /** eval/gold/{name} SQL의 첫 행 첫 열을 정답으로 쓴다. */
    private Double gold(String name) {
        try {
            String sql = Files.readString(goldDir.resolve(name), StandardCharsets.UTF_8);
            Double value = jdbc.queryForObject(sql, Double.class);
            log.info("정답 {} = {}", name, value);
            return value;
        } catch (RuntimeException e) {
            log.warn("정답 SQL 실행 실패 {}: {}", name, e.getMessage());
            throw e;
        } catch (IOException e) {
            throw new IllegalStateException("정답 SQL을 읽을 수 없습니다: " + name, e);
        }
    }

    private static String gitCommit() {
        try {
            Process p = new ProcessBuilder("git", "rev-parse", "--short", "HEAD").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return p.waitFor() == 0 ? out : "알 수 없음";
        } catch (IOException | InterruptedException e) {
            return "알 수 없음";
        }
    }
}

package kr.jeju.agent.agent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

import kr.jeju.agent.chart.ChartAssembler;
import kr.jeju.agent.metric.AreaCustomerMetric;
import kr.jeju.agent.metric.CompetitionMetric;
import kr.jeju.agent.metric.IndustryTourismMetric;

/**
 * 에이전트 조립. LLM 공급자 빈이 있으면 Spring AI 게이트웨이를, 없으면 NoLlmGateway를 쓴다.
 * 그래서 LLM 키가 없어도 앱이 뜨고, 채팅은 503으로 응답한다.
 */
@Configuration
public class AgentConfig {

    @Bean
    ChatMemory chatMemory() {
        // 이어지는 질문에 필요한 최근 3번의 질문과 답만 기억한다. 오래된 대화가 새 답변에 섞이지 않게 짧게 둔다.
        return MessageWindowChatMemory.builder().maxMessages(6).build();
    }

    @Bean
    LlmGateway llmGateway(ObjectProvider<ChatModel> chatModel, ChatMemory chatMemory,
            @Value("${app.llm.timeout-seconds:50}") long timeoutSeconds,
            @Value("${spring.ai.google.genai.chat.model:}") String primaryModel,
            @Value("${app.llm.fallback-models:}") List<String> fallbackModels) {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            return new NoLlmGateway();
        }
        // 기본 모델이 할당량 초과(429)나 과부하(503)로 실패하면 예비 모델로 넘어간다.
        List<String> models = new ArrayList<>();
        models.add(primaryModel);
        models.addAll(fallbackModels);
        return new SpringAiLlmGateway(ChatClient.create(model), chatMemory, Duration.ofSeconds(timeoutSeconds), models);
    }

    @Bean
    ChartAssembler chartAssembler() {
        return new ChartAssembler();
    }

    @Bean
    ChatAgent chatAgent(LlmGateway llmGateway, CompetitionMetric competition, AreaCustomerMetric areaCustomers,
            IndustryTourismMetric industryTourism, ChartAssembler chartAssembler,
            @Value("${app.llm.max-tool-calls:6}") int maxToolCalls, JdbcClient jdbc) {
        return new AgentService(llmGateway, competition, areaCustomers, industryTourism, chartAssembler,
                prompt("system.md") + "\n\n" + prompt("refusal.md"), maxToolCalls, code -> areaName(jdbc, code));
    }

    /** 답변에 "해당 동네" 대신 지역 이름을 쓰도록 코드를 이름으로 바꾼다. 데이터가 없으면 null. */
    static String areaName(JdbcClient jdbc, String code) {
        try {
            return jdbc.sql("SELECT MIN(emd_name) FROM ref_area WHERE emd_code = ?").param(code)
                    .query(String.class).optional().orElse(null);
        } catch (DataAccessException e) {
            return null;
        }
    }

    static String prompt(String name) {
        try {
            return new ClassPathResource("prompts/" + name).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 파일을 읽을 수 없습니다: " + name, e);
        }
    }
}

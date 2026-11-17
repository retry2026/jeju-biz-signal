package kr.jeju.agent.agent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

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
        return MessageWindowChatMemory.builder().maxMessages(10).build();
    }

    @Bean
    LlmGateway llmGateway(ObjectProvider<ChatModel> chatModel, ChatMemory chatMemory,
            @Value("${app.llm.timeout-seconds:50}") long timeoutSeconds) {
        ChatModel model = chatModel.getIfAvailable();
        return model == null ? new NoLlmGateway()
                : new SpringAiLlmGateway(ChatClient.create(model), chatMemory, Duration.ofSeconds(timeoutSeconds));
    }

    @Bean
    ChartAssembler chartAssembler() {
        return new ChartAssembler();
    }

    @Bean
    ChatAgent chatAgent(LlmGateway llmGateway, CompetitionMetric competition, AreaCustomerMetric areaCustomers,
            IndustryTourismMetric industryTourism, ChartAssembler chartAssembler,
            @Value("${app.llm.max-tool-calls:6}") int maxToolCalls) {
        return new AgentService(llmGateway, competition, areaCustomers, industryTourism, chartAssembler,
                prompt("system.md") + "\n\n" + prompt("refusal.md"), maxToolCalls);
    }

    static String prompt(String name) {
        try {
            return new ClassPathResource("prompts/" + name).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 파일을 읽을 수 없습니다: " + name, e);
        }
    }
}

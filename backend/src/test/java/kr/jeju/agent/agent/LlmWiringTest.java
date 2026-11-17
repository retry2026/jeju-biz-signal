package kr.jeju.agent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** LLM_PROVIDER=google-genai와 키를 주면 실제 Gemini 게이트웨이가 연결되는지 확인한다. 네트워크 호출은 하지 않는다. */
@SpringBootTest(properties = {
        "spring.ai.model.chat=google-genai",
        "spring.ai.google.genai.api-key=test-key-not-real"
})
class LlmWiringTest {

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry registry) throws Exception {
        Path dir = Files.createTempDirectory("jeju-wiring");
        registry.add("app.data-dir", () -> dir.toString().replace('\\', '/'));
    }

    @Autowired
    LlmGateway llmGateway;

    @Autowired
    ChatAgent chatAgent;

    @Test
    void 공급자와_키가_있으면_SpringAi_게이트웨이를_쓴다() {
        assertThat(llmGateway).isInstanceOf(SpringAiLlmGateway.class);
        assertThat(chatAgent).isInstanceOf(AgentService.class);
    }
}

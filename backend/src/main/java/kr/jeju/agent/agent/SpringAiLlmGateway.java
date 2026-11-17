package kr.jeju.agent.agent;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Spring AI ChatClient로 LLM을 부른다. 세션별 대화 기억, 도구 호출, 구조화 출력을 쓰고,
 * 정해진 시간 안에 답이 없으면 LlmUnavailableException을 던진다.
 */
public class SpringAiLlmGateway implements LlmGateway {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final Duration timeout;
    private final List<String> models;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SpringAiLlmGateway(ChatClient chatClient, ChatMemory chatMemory, Duration timeout, List<String> models) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        this.timeout = timeout;
        this.models = models.stream().filter(m -> m != null && !m.isBlank()).map(String::trim).distinct().toList();
    }

    @Override
    public AgentReply ask(String sessionId, String systemPrompt, String userMessage, Object tools) {
        return ask(sessionId, systemPrompt, userMessage, tools, 0);
    }

    /**
     * 0, 1번째 시도는 기본 모델, 그다음부터 예비 모델을 순서대로 쓴다.
     * 기본 모델의 실패는 응답 JSON이 가끔 끊기는 일시 오류가 많아 한 번은 같은 모델로 다시 부른다.
     */
    @Override
    public AgentReply ask(String sessionId, String systemPrompt, String userMessage, Object tools, int attempt) {
        var spec = chatClient.prompt();
        if (!models.isEmpty()) {
            spec = spec.options(ChatOptions.builder().model(modelFor(attempt)));
        }
        var request = spec;
        CompletableFuture<AgentReply> call = CompletableFuture.supplyAsync(() -> request
                .system(systemPrompt)
                .user(userMessage)
                .tools(tools)
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .entity(AgentReply.class), executor);
        try {
            return call.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            call.cancel(true);
            throw new LlmUnavailableException("LLM 응답 시간이 " + timeout.toSeconds() + "초를 넘었습니다.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmUnavailableException("LLM 호출이 중단되었습니다.", e);
        } catch (ExecutionException e) {
            throw new LlmUnavailableException("LLM 호출에 실패했습니다.", e.getCause());
        }
    }

    @Override
    public int maxAttempts() {
        return Math.max(2, models.size() + 1);
    }

    /** attempt번째 시도에 쓸 모델 이름 (로그용). */
    public String modelFor(int attempt) {
        return models.isEmpty() ? null : models.get(Math.min(Math.max(0, attempt - 1), models.size() - 1));
    }

    @Override
    public void clear(String sessionId) {
        chatMemory.clear(sessionId);
    }
}

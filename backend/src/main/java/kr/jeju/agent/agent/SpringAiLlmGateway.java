package kr.jeju.agent.agent;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;

/**
 * Spring AI ChatClient로 LLM을 부른다. 세션별 대화 기억, 도구 호출, 구조화 출력을 쓰고,
 * 정해진 시간 안에 답이 없으면 LlmUnavailableException을 던진다.
 */
public class SpringAiLlmGateway implements LlmGateway {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final Duration timeout;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SpringAiLlmGateway(ChatClient chatClient, ChatMemory chatMemory, Duration timeout) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        this.timeout = timeout;
    }

    @Override
    public AgentReply ask(String sessionId, String systemPrompt, String userMessage, Object tools) {
        CompletableFuture<AgentReply> call = CompletableFuture.supplyAsync(() -> chatClient.prompt()
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
    public void clear(String sessionId) {
        chatMemory.clear(sessionId);
    }
}

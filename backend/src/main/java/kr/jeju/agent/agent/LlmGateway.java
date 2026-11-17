package kr.jeju.agent.agent;

/** LLM 호출 경계. 테스트에서는 가짜로 바꾼다. 실패하면 LlmUnavailableException을 던진다. */
public interface LlmGateway {

    AgentReply ask(String sessionId, String systemPrompt, String userMessage, Object tools);

    /**
     * attempt번째 시도(0부터). 실패 후 다시 부를 때 다른 모델로 바꿀 수 있는 구현은 이것을 덮어쓴다.
     * 기본은 같은 모델로 다시 부른다.
     */
    default AgentReply ask(String sessionId, String systemPrompt, String userMessage, Object tools, int attempt) {
        return ask(sessionId, systemPrompt, userMessage, tools);
    }

    /** 한 질문에서 LLM을 최대 몇 번까지 시도할지. */
    default int maxAttempts() {
        return 2;
    }

    /** 세션의 대화 기억을 지운다. */
    default void clear(String sessionId) {
    }
}

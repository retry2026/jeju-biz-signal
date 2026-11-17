package kr.jeju.agent.agent;

/** LLM 호출 경계. 테스트에서는 가짜로 바꾼다. 실패하면 LlmUnavailableException을 던진다. */
public interface LlmGateway {

    AgentReply ask(String sessionId, String systemPrompt, String userMessage, Object tools);

    /** 세션의 대화 기억을 지운다. */
    default void clear(String sessionId) {
    }
}

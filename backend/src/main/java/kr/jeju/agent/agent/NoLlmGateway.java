package kr.jeju.agent.agent;

/** LLM 공급자 starter가 없을 때 쓰는 게이트웨이. 항상 503이 되도록 예외를 던진다. */
public class NoLlmGateway implements LlmGateway {

    @Override
    public AgentReply ask(String sessionId, String systemPrompt, String userMessage, Object tools) {
        throw new LlmUnavailableException("LLM 공급자가 설정되지 않았습니다.", null);
    }
}

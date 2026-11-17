package kr.jeju.agent.agent;

/** ③과 ④의 접점. web은 자기 DTO를 AgentRequest로 바꿔 이 인터페이스를 부르고, AgentAnswer를 자기 DTO로 바꿔 응답한다. */
public interface ChatAgent {

    AgentAnswer answer(AgentRequest request);

    /** 화면 리셋이나 세션 만료 때 web이 불러 대화 기억을 지운다. */
    void endSession(String sessionId);
}

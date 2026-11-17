package kr.jeju.agent.agent;

/** LLM API 오류, 시간 초과, 공급자 미설정. web에서 503 LLM_UNAVAILABLE로 응답한다. */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

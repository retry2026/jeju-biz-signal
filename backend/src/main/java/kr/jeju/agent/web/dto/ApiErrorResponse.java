package kr.jeju.agent.web.dto;

public record ApiErrorResponse(
        String code,
        String message,
        String field) {
}

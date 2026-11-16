package kr.jeju.agent.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank String sessionId,
        @NotBlank @Pattern(regexp = "STARTUP|EXISTING") String userType,
        @NotBlank String area,
        @NotBlank String industry,
        @Valid MyStoreRequest myStore,
        @NotBlank @Size(max = 500) String message) {
}

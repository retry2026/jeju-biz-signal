package kr.jeju.agent.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record MonthlySaleRequest(
        @NotNull @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String month,
        @NotNull @PositiveOrZero Long amount) {
}

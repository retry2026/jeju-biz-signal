package kr.jeju.agent.web.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record MyStoreRequest(
        @Size(min = 1, max = 12) List<@Valid MonthlySaleRequest> monthlySales) {
}

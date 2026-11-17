package kr.jeju.agent.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

class ChatRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 질문은_500자까지_허용하고_초과하면_검증에_실패한다() {
        assertThat(validator.validate(request("가".repeat(500)))).isEmpty();
        assertThat(validator.validate(request("가".repeat(501))))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("message"));
    }

    @Test
    void 월매출은_월_형식과_최대_12개를_검증한다() {
        var invalidMonth = new ChatRequest(
                "8f1c2a4e-1b7d-4c55-9a0e-2d3f4b5c6d7e",
                "EXISTING",
                "50110250",
                "CAFE",
                new MyStoreRequest(List.of(new MonthlySaleRequest("2026-1", 100_000L))),
                "매출이 줄었어요");
        var tooManyMonths = new ChatRequest(
                "8f1c2a4e-1b7d-4c55-9a0e-2d3f4b5c6d7e",
                "EXISTING",
                "50110250",
                "CAFE",
                new MyStoreRequest(java.util.Collections.nCopies(13, new MonthlySaleRequest("2026-01", 100_000L))),
                "매출이 줄었어요");

        assertThat(validator.validate(invalidMonth))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                        .isEqualTo("myStore.monthlySales[0].month"));
        assertThat(validator.validate(tooManyMonths))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                        .isEqualTo("myStore.monthlySales"));
    }

    @Test
    void 월매출_월은_01부터_12까지만_허용한다() {
        assertThat(validator.validate(requestWithMonth("2026-01"))).isEmpty();
        assertThat(validator.validate(requestWithMonth("2026-12"))).isEmpty();

        for (String month : List.of("2026-00", "2026-13", "2026-99")) {
            assertThat(validator.validate(requestWithMonth(month)))
                    .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                            .isEqualTo("myStore.monthlySales[0].month"));
        }
    }

    private ChatRequest requestWithMonth(String month) {
        return new ChatRequest(
                "8f1c2a4e-1b7d-4c55-9a0e-2d3f4b5c6d7e",
                "EXISTING",
                "50110250",
                "CAFE",
                new MyStoreRequest(List.of(new MonthlySaleRequest(month, 100_000L))),
                "매출이 줄었어요");
    }

    private ChatRequest request(String message) {
        return new ChatRequest(
                "8f1c2a4e-1b7d-4c55-9a0e-2d3f4b5c6d7e",
                "STARTUP",
                "50110660",
                "CAFE",
                null,
                message);
    }
}

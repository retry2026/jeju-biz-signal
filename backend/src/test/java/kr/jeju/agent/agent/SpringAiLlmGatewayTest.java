package kr.jeju.agent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

class SpringAiLlmGatewayTest {

    @Test
    void 기본_모델로_두_번_시도한_뒤_예비_모델을_순서대로_쓴다() {
        var gw = new SpringAiLlmGateway(null, null, Duration.ofSeconds(1),
                List.of("lite", "", "preview", "flash", "lite"));

        assertThat(gw.maxAttempts()).isEqualTo(4);
        assertThat(List.of(gw.modelFor(0), gw.modelFor(1), gw.modelFor(2), gw.modelFor(3)))
                .containsExactly("lite", "lite", "preview", "flash");
    }

    @Test
    void 모델_목록이_없으면_공급자_기본_모델로_두_번까지_시도한다() {
        var gw = new SpringAiLlmGateway(null, null, Duration.ofSeconds(1), List.of());

        assertThat(gw.maxAttempts()).isEqualTo(2);
        assertThat(gw.modelFor(1)).isNull();
    }
}

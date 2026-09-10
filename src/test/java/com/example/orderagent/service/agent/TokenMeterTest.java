package com.example.orderagent.service.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.orderagent.config.PriceTableProperties;
import com.example.orderagent.config.PriceTableProperties.ModelPrice;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TokenMeterTest {

    @Test
    void pricesPromptAndCompletionTokensSeparately() {
        PriceTableProperties prices = new PriceTableProperties(
                Map.of("test-model", new ModelPrice(new BigDecimal("1.00"), new BigDecimal("5.00"))));
        TokenMeter meter = new TokenMeter(prices, new SimpleMeterRegistry());

        // 1,000,000 prompt tokens at $1.00/M + 1,000,000 completion tokens at $5.00/M
        BigDecimal cost = meter.cost("test-model", 1_000_000, 1_000_000);

        assertThat(cost).isEqualByComparingTo("6.00");
    }

    @Test
    void pricesAnUnknownModelAsZeroRatherThanThrowing() {
        TokenMeter meter = new TokenMeter(new PriceTableProperties(Map.of()), new SimpleMeterRegistry());

        BigDecimal cost = meter.cost("some-unlisted-model", 1000, 1000);

        assertThat(cost).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void recordsMicrometerCountersForTokensAndCost() {
        PriceTableProperties prices = new PriceTableProperties(
                Map.of("test-model", new ModelPrice(new BigDecimal("1.00"), new BigDecimal("5.00"))));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TokenMeter meter = new TokenMeter(prices, registry);

        meter.recordUsage("test-model", 1000, 500);

        assertThat(registry.counter("orderagent.tokens", "model", "test-model", "type", "prompt").count())
                .isEqualTo(1000.0);
        assertThat(registry.counter("orderagent.tokens", "model", "test-model", "type", "completion").count())
                .isEqualTo(500.0);
        assertThat(registry.counter("orderagent.cost", "model", "test-model").count()).isGreaterThan(0.0);
    }
}

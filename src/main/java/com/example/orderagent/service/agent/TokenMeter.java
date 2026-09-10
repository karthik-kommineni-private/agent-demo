package com.example.orderagent.service.agent;

import com.example.orderagent.config.PriceTableProperties;
import com.example.orderagent.config.PriceTableProperties.ModelPrice;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.MathContext;
import org.springframework.stereotype.Component;

/**
 * Turns token counts from a {@code ChatResponse} into a dollar figure, and
 * publishes both as Micrometer counters so cost is visible outside the
 * response body too — at {@code /actuator/metrics/orderagent.tokens} and
 * {@code /actuator/metrics/orderagent.cost}.
 *
 * <p>"Token" here means a chunk of text the model bills by — roughly a
 * word or part of one, not a character. A model call's cost is
 * {@code (prompt tokens × input price) + (completion tokens × output
 * price)}, priced separately because providers charge more for what the
 * model generates than for what it reads.
 *
 * <p><b>How it fails:</b> a model name with no entry in
 * {@link PriceTableProperties} prices as zero rather than throwing —
 * losing a cost figure for one call is better than failing the request
 * over a pricing table gap.
 */
@Component
public class TokenMeter {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);

    private final PriceTableProperties priceTable;
    private final MeterRegistry meterRegistry;

    public TokenMeter(PriceTableProperties priceTable, MeterRegistry meterRegistry) {
        this.priceTable = priceTable;
        this.meterRegistry = meterRegistry;
    }

    /**
     * The dollar cost of one model call.
     *
     * @param model            the model that was called
     * @param promptTokens     tokens in the request sent to the model
     * @param completionTokens tokens the model generated in response
     * @return the cost, or zero if {@code model} has no price configured
     */
    public BigDecimal cost(String model, int promptTokens, int completionTokens) {
        ModelPrice price = priceTable.models() == null ? null : priceTable.models().get(model);
        if (price == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal inputCost = price.inputPerMillionTokens()
                .multiply(BigDecimal.valueOf(promptTokens))
                .divide(ONE_MILLION, MathContext.DECIMAL64);
        BigDecimal outputCost = price.outputPerMillionTokens()
                .multiply(BigDecimal.valueOf(completionTokens))
                .divide(ONE_MILLION, MathContext.DECIMAL64);
        return inputCost.add(outputCost);
    }

    /**
     * Records one model call's token usage and cost as Micrometer counters,
     * tagged by model so cost-by-model is queryable without touching a log.
     */
    public void recordUsage(String model, int promptTokens, int completionTokens) {
        meterRegistry.counter("orderagent.tokens", "model", model, "type", "prompt").increment(promptTokens);
        meterRegistry.counter("orderagent.tokens", "model", model, "type", "completion").increment(completionTokens);
        meterRegistry
                .counter("orderagent.cost", "model", model)
                .increment(cost(model, promptTokens, completionTokens).doubleValue());
    }
}

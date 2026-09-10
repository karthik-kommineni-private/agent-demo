package com.example.orderagent.config;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dollar cost per model, from {@code application.yml} — never hardcoded in
 * {@code TokenMeter}, so a price change or a new model is a config change.
 *
 * <p>Prices here are illustrative, not contractual: check the provider's
 * current pricing page before treating a number this class produces as a
 * real bill.
 *
 * @param models model name to its per-million-token input/output prices
 */
@ConfigurationProperties(prefix = "orderagent.pricing")
public record PriceTableProperties(Map<String, ModelPrice> models) {

    /**
     * @param inputPerMillionTokens  dollars per 1,000,000 prompt tokens
     * @param outputPerMillionTokens dollars per 1,000,000 completion tokens
     */
    public record ModelPrice(BigDecimal inputPerMillionTokens, BigDecimal outputPerMillionTokens) {
    }
}

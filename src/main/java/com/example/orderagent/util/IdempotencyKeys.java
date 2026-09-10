package com.example.orderagent.util;

/**
 * Validates the shape of a caller-supplied idempotency key.
 *
 * <p>We don't generate keys — the model supplies one with every
 * {@code issueRefund} call, the same way a caller of a real payments API
 * supplies its own. This only guards against something too weak to be
 * useful as a dedupe key (blank, or absurdly long) before it's used to key
 * the idempotency store.
 */
public final class IdempotencyKeys {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;

    private IdempotencyKeys() {
    }

    public static boolean isValid(String key) {
        return key != null && key.length() >= MIN_LENGTH && key.length() <= MAX_LENGTH;
    }
}

package org.example.payments.core.ports.in;

import org.example.payments.core.domain.PaymentId;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * A refund of a payment that settled outside the system, to be recorded. A null refundAmount refunds whatever is
 * still refundable. The checks that need the payment (currency decimals, refundedAt after settledAt, what's left
 * to refund) are made when it's recorded.
 */
public record RecordRefundCommand(
        PaymentId paymentId,
        String externalId,
        Instant refundedAt,
        @Nullable BigDecimal refundAmount) {

    /** refunds.total_amount is numeric(19,4): at most 15 integer digits. */
    private static final BigDecimal MAX_REFUND_AMOUNT = new BigDecimal("1000000000000000");
    /** Longer values overflow the btree entry of the (merchant_id, external_id) unique index. */
    private static final int MAX_EXTERNAL_ID_LENGTH = 255;
    /** Within Postgres timestamptz range, which ends in year 294276. */
    private static final Instant MIN_REFUNDED_AT = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAX_REFUNDED_AT = Instant.parse("+10000-01-01T00:00:00Z");

    public RecordRefundCommand {
        if (externalId.isBlank()) {
            throw new InvalidPaymentException("externalId must not be blank");
        }
        if (externalId.length() > MAX_EXTERNAL_ID_LENGTH) {
            throw new InvalidPaymentException("externalId must be at most " + MAX_EXTERNAL_ID_LENGTH
                                              + " characters, got " + externalId.length());
        }
        if (refundedAt.isBefore(MIN_REFUNDED_AT) || !refundedAt.isBefore(MAX_REFUNDED_AT)) {
            throw new InvalidPaymentException("refundedAt must be between years 0001 and 9999: " + refundedAt);
        }
        // Postgres timestamptz stores microseconds; truncating here makes a resent value equal the stored one.
        refundedAt = refundedAt.truncatedTo(ChronoUnit.MICROS);
        if (refundAmount != null) {
            if (refundAmount.signum() <= 0) {
                throw new InvalidPaymentException("refundAmount must be positive: " + refundAmount);
            }
            if (refundAmount.compareTo(MAX_REFUND_AMOUNT) >= 0) {
                throw new InvalidPaymentException("refundAmount is too large (must be below 10^15): " + refundAmount);
            }
        }
    }
}

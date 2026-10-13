package org.example.payments.rest;

import org.example.payments.core.domain.PaymentId;
import org.example.payments.core.ports.in.PaymentNotFoundException;
import org.example.payments.core.ports.in.RecordRefundCommand;
import org.jspecify.annotations.Nullable;

import static org.example.payments.rest.RequestFields.parseDecimal;
import static org.example.payments.rest.RequestFields.parseInstant;
import static org.example.payments.rest.RequestFields.required;

/**
 * POST /payments/{paymentId}/refund request body. The amount is a string so no precision is lost in JSON;
 * leaving it out refunds whatever is still refundable.
 */
public record RefundRecord(
        @Nullable String externalId,
        @Nullable String refundedAt,
        @Nullable String refundAmount) {

    public RecordRefundCommand toCommand(String paymentId) {
        return new RecordRefundCommand(
                parsePaymentId(paymentId),
                required(externalId, "externalId"),
                parseInstant(required(refundedAt, "refundedAt"), "refundedAt"),
                refundAmount == null ? null : parseDecimal(refundAmount, "refundAmount"));
    }

    /** A malformed id can't name a payment, so it's a 404 like an unknown one. */
    private static PaymentId parsePaymentId(String value) {
        try {
            return new PaymentId(value);
        } catch (IllegalArgumentException e) {
            throw new PaymentNotFoundException(value);
        }
    }
}

package org.example.payments.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import org.example.payments.core.domain.PaymentId;
import org.example.payments.core.ports.in.PaymentNotFoundException;
import org.example.payments.core.ports.in.RecordRefundCommand;
import org.jspecify.annotations.Nullable;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import static org.example.payments.rest.RequestFields.parseDecimal;
import static org.example.payments.rest.RequestFields.parseInstant;
import static org.example.payments.rest.RequestFields.required;

/**
 * POST /payments/{paymentId}/refund request body. The amount is a string so no precision is lost in JSON;
 * leaving it out refunds whatever is still refundable.
 */
public record RefundRecord(
        @Schema(requiredMode = REQUIRED, example = "EXT-DEMO-REFUND-1",
                description = "Your ID for the refund. Repeating a request with it returns the same refund.")
        @Nullable String externalId,
        @Schema(requiredMode = REQUIRED, example = "2026-10-02T09:00:00+02:00",
                description = "ISO-8601 timestamp with an offset")
        @Nullable String refundedAt,
        @Schema(example = "19.00", description = "Leave out to refund whatever is still refundable")
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

package org.example.payments.rest;

import org.example.payments.core.domain.Refund;

import java.time.format.DateTimeFormatter;

import static org.example.payments.rest.ResponseFields.money;
import static org.example.payments.rest.ResponseFields.rate;

/** 201 body for POST /payments/{paymentId}/refund: amounts as strings, refundedAt in UTC. */
public record RefundResponse(
        String refundId,
        String externalId,
        String paymentId,
        String merchantId,
        String refundedAt,
        String currency,
        String refundAmount,
        String taxRate,
        String taxAmount,
        String netAmount,
        String feeAmount,
        String merchantAmount,
        String taxCountry) {

    public static RefundResponse fromDomain(Refund refund) {
        int digits = refund.currency().getDefaultFractionDigits();
        return new RefundResponse(
                refund.refundId().value(),
                refund.externalId(),
                refund.paymentId().value(),
                refund.merchantId(),
                DateTimeFormatter.ISO_INSTANT.format(refund.refundedAt()),
                refund.currency().getCurrencyCode(),
                money(refund.totalAmount(), digits),
                rate(refund.taxRate()),
                money(refund.taxAmount(), digits),
                money(refund.netAmount(), digits),
                money(refund.feeAmount(), digits),
                money(refund.merchantAmount(), digits),
                refund.taxCountry());
    }
}

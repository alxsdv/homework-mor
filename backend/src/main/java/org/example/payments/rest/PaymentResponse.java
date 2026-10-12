package org.example.payments.rest;

import org.example.payments.core.domain.Payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

/** 201 body for POST /payments: amounts as strings, settledAt in UTC. */
public record PaymentResponse(
        String paymentId,
        String externalId,
        String merchantId,
        String settledAt,
        String currency,
        String totalAmount,
        String taxRate,
        String taxAmount,
        String netAmount,
        String feeAmount,
        String merchantAmount,
        String taxCountry,
        Customer customer) {

    public static PaymentResponse fromDomain(Payment payment) {
        int digits = payment.currency().getDefaultFractionDigits();
        return new PaymentResponse(
                payment.paymentId().value(),
                payment.externalId(),
                payment.merchantId(),
                DateTimeFormatter.ISO_INSTANT.format(payment.settledAt()),
                payment.currency().getCurrencyCode(),
                money(payment.totalAmount(), digits),
                rate(payment.taxRate()),
                money(payment.taxAmount(), digits),
                money(payment.netAmount(), digits),
                money(payment.feeAmount(), digits),
                money(payment.merchantAmount(), digits),
                payment.taxCountry(),
                Customer.fromDomain(payment.customer()));
    }

    private static String money(BigDecimal amount, int digits) {
        return amount.setScale(digits, RoundingMode.UNNECESSARY).toPlainString();
    }

    /** numeric(9,6) comes back as 0.190000; show 0.19. */
    private static String rate(BigDecimal rate) {
        return rate.stripTrailingZeros().toPlainString();
    }
}

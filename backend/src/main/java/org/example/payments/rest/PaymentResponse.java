package org.example.payments.rest;

import org.example.payments.core.domain.Payment;

import java.time.format.DateTimeFormatter;

import static org.example.payments.rest.ResponseFields.money;
import static org.example.payments.rest.ResponseFields.rate;

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
}

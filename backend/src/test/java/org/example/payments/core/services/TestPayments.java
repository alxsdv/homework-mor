package org.example.payments.core.services;

import org.example.payments.core.domain.Contact;
import org.example.payments.core.domain.Customer;
import org.example.payments.core.domain.Payment;
import org.example.payments.core.domain.PaymentAmounts;
import org.example.payments.core.domain.PaymentId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

final class TestPayments {

    private static final Currency EUR = Currency.getInstance("EUR");
    private static final BigDecimal TAX_RATE = new BigDecimal("0.19");
    private static final BigDecimal FEE_RATE = new BigDecimal("0.03");

    private TestPayments() {}

    /** A German customer's EUR payment at 19% tax and a 3% fee, settled 2026-09-30T21:30:00Z. */
    static Payment payment(String paymentId, String externalId, String merchantId, String totalAmount) {
        PaymentAmounts amounts = PaymentAmounts.calculate(new BigDecimal(totalAmount), TAX_RATE, FEE_RATE, EUR);
        Customer customer = new Customer("Anna", "Schmidt", "anna.schmidt@example.com",
                                         new Contact("Hauptstrasse 12", "Wohnung 4", "DE", "10115"));
        return new Payment(new PaymentId(paymentId), externalId, Instant.parse("2026-09-30T21:30:00Z"), merchantId,
                           customer, "DE", EUR, amounts.totalAmount(), TAX_RATE, amounts.taxAmount(),
                           amounts.netAmount(), FEE_RATE, amounts.feeAmount(), amounts.merchantAmount());
    }
}

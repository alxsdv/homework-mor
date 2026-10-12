package org.example.payments.core.services;

import org.example.payments.core.domain.Contact;
import org.example.payments.core.domain.Customer;
import org.example.payments.core.ports.in.RecordSettledPaymentCommand;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

final class TestCommands {

    private TestCommands() {}

    /** A German customer's payment taxed at 19%. */
    static RecordSettledPaymentCommand command(String externalId, String merchantId, String totalAmount,
                                               String currency, String settledAt) {
        Customer customer = new Customer("Anna", "Schmidt", "anna.schmidt@example.com",
                                         new Contact("Hauptstrasse 12", "Wohnung 4", "DE", "10115"));
        return new RecordSettledPaymentCommand(externalId, Instant.parse(settledAt), merchantId, customer,
                                               Currency.getInstance(currency), new BigDecimal(totalAmount),
                                               new BigDecimal("0.19"));
    }

    static RecordSettledPaymentCommand command(String externalId) {
        return command(externalId, "MERCH-001", "119.00", "EUR", "2026-09-30T21:30:00Z");
    }
}

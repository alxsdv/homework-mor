package org.example.payments.rest;

import org.example.payments.core.ports.in.RecordSettledPaymentCommand;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Currency;

public record PaymentRecord(
        String externalId,
        String settledAt,
        String merchantId,
        Customer customer,
        String currency,
        String totalAmount,
        String taxRate) {

    public RecordSettledPaymentCommand toCommand() {
        return new RecordSettledPaymentCommand(externalId, OffsetDateTime.parse(settledAt).toInstant(), merchantId,
                                               customer.toDomain(), Currency.getInstance(currency),
                                               new BigDecimal(totalAmount), new BigDecimal(taxRate));
    }
}

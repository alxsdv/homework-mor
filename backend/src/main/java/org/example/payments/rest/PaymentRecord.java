package org.example.payments.rest;

import org.example.payments.core.ports.in.RecordSettledPaymentCommand;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;

public record PaymentRecord(
        String externalId,
        String paymentDate,
        String merchantId,
        Customer customer,
        String currency,
        String totalAmount,
        String taxRate) {

    public RecordSettledPaymentCommand toCommand() {
        return new RecordSettledPaymentCommand(externalId, LocalDate.parse(paymentDate), merchantId,
                                               customer.toDomain(), Currency.getInstance(currency),
                                               new BigDecimal(totalAmount), new BigDecimal(taxRate));
    }
}

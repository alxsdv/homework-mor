package org.example.payments.rest;

import org.example.payments.core.ports.in.InvalidPaymentException;
import org.example.payments.core.ports.in.RecordSettledPaymentCommand;
import org.jspecify.annotations.Nullable;

import java.util.Currency;

import static org.example.payments.rest.RequestFields.parseDecimal;
import static org.example.payments.rest.RequestFields.parseInstant;
import static org.example.payments.rest.RequestFields.required;

/** POST /payments request body. Amounts are strings so no precision is lost in JSON. */
public record PaymentRecord(
        @Nullable String externalId,
        @Nullable String settledAt,
        @Nullable String merchantId,
        @Nullable Customer customer,
        @Nullable String currency,
        @Nullable String totalAmount,
        @Nullable String taxRate) {

    public RecordSettledPaymentCommand toCommand() {
        return new RecordSettledPaymentCommand(
                required(externalId, "externalId"),
                parseInstant(required(settledAt, "settledAt"), "settledAt"),
                required(merchantId, "merchantId"),
                required(customer, "customer").toDomain(),
                parseCurrency(required(currency, "currency")),
                parseDecimal(required(totalAmount, "totalAmount"), "totalAmount"),
                parseDecimal(required(taxRate, "taxRate"), "taxRate"));
    }

    private static Currency parseCurrency(String value) {
        try {
            return Currency.getInstance(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidPaymentException("currency must be an ISO 4217 code, got: " + value);
        }
    }
}

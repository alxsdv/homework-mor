package org.example.payouts.rest;

import org.example.payouts.core.ports.in.InvalidPayoutException;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Currency;

/** Turns raw JSON field values into typed ones, or into a 400 naming the field. */
final class RequestFields {

    private RequestFields() {}

    /** JSON fields can be absent whatever the Java type says; turn that into a 400 naming the field. */
    static <T> T required(@Nullable T value, String field) {
        if (value == null) {
            throw new InvalidPayoutException(field + " is required");
        }
        return value;
    }

    static Instant parseInstant(String value, String field) {
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            throw new InvalidPayoutException(
                    field + " must be an ISO-8601 timestamp with an offset, e.g. 2026-10-03T09:00:00+02:00, got: "
                    + value);
        }
    }

    static BigDecimal parseDecimal(String value, String field) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new InvalidPayoutException(field + " must be a decimal number, got: " + value);
        }
    }

    static Currency parseCurrency(String value) {
        try {
            return Currency.getInstance(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidPayoutException("currency must be an ISO 4217 code, got: " + value);
        }
    }
}

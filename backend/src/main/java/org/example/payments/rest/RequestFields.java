package org.example.payments.rest;

import org.example.payments.core.ports.in.InvalidPaymentException;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/** Turns raw JSON field values into typed ones, or into a 400 naming the field. */
final class RequestFields {

    private RequestFields() {}

    /** JSON fields can be absent whatever the Java type says; turn that into a 400 naming the field. */
    static <T> T required(@Nullable T value, String field) {
        if (value == null) {
            throw new InvalidPaymentException(field + " is required");
        }
        return value;
    }

    static Instant parseInstant(String value, String field) {
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            throw new InvalidPaymentException(
                    field + " must be an ISO-8601 timestamp with an offset, e.g. 2026-09-30T23:30:00+02:00, got: "
                    + value);
        }
    }

    static BigDecimal parseDecimal(String value, String field) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new InvalidPaymentException(field + " must be a decimal number, got: " + value);
        }
    }
}

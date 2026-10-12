package org.example.payments.rest;

import org.example.payments.core.ports.in.InvalidPaymentException;
import org.jspecify.annotations.Nullable;

final class RequestFields {

    private RequestFields() {}

    /** JSON fields can be absent whatever the Java type says; turn that into a 400 naming the field. */
    static <T> T required(@Nullable T value, String field) {
        if (value == null) {
            throw new InvalidPaymentException(field + " is required");
        }
        return value;
    }
}

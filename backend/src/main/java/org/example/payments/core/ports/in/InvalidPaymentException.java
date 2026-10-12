package org.example.payments.core.ports.in;

/** The request can't be recorded as given: a missing, malformed or out-of-range value. Maps to 400. */
public class InvalidPaymentException extends RuntimeException {

    public InvalidPaymentException(String message) {
        super(message);
    }
}

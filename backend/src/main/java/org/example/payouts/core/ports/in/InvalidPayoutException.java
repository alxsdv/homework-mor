package org.example.payouts.core.ports.in;

/** The request can't be recorded as given: a missing, malformed or out-of-range value. Maps to 400. */
public class InvalidPayoutException extends RuntimeException {

    public InvalidPayoutException(String message) {
        super(message);
    }
}

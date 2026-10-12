package org.example.payments.core.ports.in;

/** A payment with this merchantId + externalId exists but differs in amount, currency or settledAt. Maps to 409. */
public class PaymentConflictException extends RuntimeException {

    public PaymentConflictException(String merchantId, String externalId) {
        super("Payment with external ID " + externalId + " for merchant " + merchantId
              + " already exists with a different amount, currency or settlement time");
    }
}

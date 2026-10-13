package org.example.payments.core.ports.in;

/** A refund with this merchantId + externalId exists but differs in payment, refundedAt or amount. Maps to 409. */
public class RefundConflictException extends RuntimeException {

    public RefundConflictException(String merchantId, String externalId) {
        super("Refund with external ID " + externalId + " for merchant " + merchantId
              + " already exists for a different payment, refund time or amount");
    }
}

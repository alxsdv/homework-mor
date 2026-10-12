package org.example.payments.core.ports.out;

/**
 * Another transaction committed a payment with the same merchantId + externalId between our lookup and our insert.
 * Retrying the whole use case finds and returns that payment.
 */
public class DuplicateExternalIdException extends RuntimeException {

    private final String merchantId;
    private final String externalId;

    public DuplicateExternalIdException(String merchantId, String externalId) {
        super("Payment with external ID " + externalId + " for merchant " + merchantId + " already exists");
        this.merchantId = merchantId;
        this.externalId = externalId;
    }

    public String merchantId() {
        return merchantId;
    }

    public String externalId() {
        return externalId;
    }
}

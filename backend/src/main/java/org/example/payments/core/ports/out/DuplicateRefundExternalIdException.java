package org.example.payments.core.ports.out;

/**
 * Another transaction committed a refund with the same merchantId + externalId between our lookup and our insert.
 * Retrying the whole use case finds that refund and returns it, or reports a conflict.
 */
public class DuplicateRefundExternalIdException extends RuntimeException {

    private final String merchantId;
    private final String externalId;

    public DuplicateRefundExternalIdException(String merchantId, String externalId) {
        super("Refund with external ID " + externalId + " for merchant " + merchantId + " already exists");
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

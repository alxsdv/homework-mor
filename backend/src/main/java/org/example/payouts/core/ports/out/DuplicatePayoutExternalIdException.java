package org.example.payouts.core.ports.out;

/**
 * Another transaction committed a payout with the same merchantId + externalId between our lookup and our insert.
 * Retrying the whole use case finds that payout and returns it, or reports a conflict.
 */
public class DuplicatePayoutExternalIdException extends RuntimeException {

    private final String merchantId;
    private final String externalId;

    public DuplicatePayoutExternalIdException(String merchantId, String externalId) {
        super("Payout with external ID " + externalId + " for merchant " + merchantId + " already exists");
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

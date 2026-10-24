package org.example.payouts.core.ports.in;

/** A payout with this merchantId + externalId exists but differs in currency, amount or requestedAt. Maps to 409. */
public class PayoutConflictException extends RuntimeException {

    public PayoutConflictException(String merchantId, String externalId) {
        super("Payout with external ID " + externalId + " for merchant " + merchantId
              + " already exists for a different currency, amount or request time");
    }
}

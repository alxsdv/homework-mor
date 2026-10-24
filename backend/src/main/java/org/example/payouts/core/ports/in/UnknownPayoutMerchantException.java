package org.example.payouts.core.ports.in;

/** The merchantId isn't a known merchant. Maps to 400. */
public class UnknownPayoutMerchantException extends RuntimeException {

    public UnknownPayoutMerchantException(String merchantId) {
        super("Unknown merchant: " + merchantId);
    }
}

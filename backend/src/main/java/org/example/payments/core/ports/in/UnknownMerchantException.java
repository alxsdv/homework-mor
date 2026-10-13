package org.example.payments.core.ports.in;

/** The merchantId isn't a known merchant. Maps to 400. */
public class UnknownMerchantException extends RuntimeException {

    public UnknownMerchantException(String merchantId) {
        super("Unknown merchant: " + merchantId);
    }
}

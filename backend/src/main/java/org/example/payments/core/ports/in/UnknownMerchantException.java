package org.example.payments.core.ports.in;

/** The merchantId doesn't exist in the merchants table. Maps to 400. */
public class UnknownMerchantException extends RuntimeException {

    public UnknownMerchantException(String merchantId) {
        super("Unknown merchant: " + merchantId);
    }
}

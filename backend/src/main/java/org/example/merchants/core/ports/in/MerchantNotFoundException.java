package org.example.merchants.core.ports.in;

/** No merchant has this id. Maps to 404. */
public class MerchantNotFoundException extends RuntimeException {

    public MerchantNotFoundException(String merchantId) {
        super("Unknown merchant: " + merchantId);
    }
}

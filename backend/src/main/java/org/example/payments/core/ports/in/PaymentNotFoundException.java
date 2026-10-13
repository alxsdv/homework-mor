package org.example.payments.core.ports.in;

/** No payment has this id; a malformed id can't name one either. Maps to 404. */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String paymentId) {
        super("Payment not found: " + paymentId);
    }
}

package org.example.payments.core.ports.out;

import org.example.payments.core.domain.PaymentId;

public class DuplicatePaymentIdException extends RuntimeException {
    private final PaymentId paymentId;

    public DuplicatePaymentIdException(PaymentId paymentId) {
        super("Payment ID already exists: " + paymentId.value());
        this.paymentId = paymentId;
    }

    public PaymentId paymentId() {
        return paymentId;
    }
}

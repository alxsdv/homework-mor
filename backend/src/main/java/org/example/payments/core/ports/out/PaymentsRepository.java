package org.example.payments.core.ports.out;

import org.example.payments.core.domain.Payment;
import org.example.payments.core.domain.PaymentId;

import java.util.Optional;

public interface PaymentsRepository {
    PaymentId nextId();
    Optional<Payment> findByMerchantAndExternalId(String merchantId, String externalId);

    /**
     * SELECT … FOR UPDATE by public id. Call it inside a transaction: the row lock is held until that transaction
     * ends, which serializes the payment's refunds.
     */
    Optional<Payment> findByIdForUpdate(PaymentId paymentId);

    Payment addPayment(Payment payment);
}

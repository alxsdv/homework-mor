package org.example.payments.core.ports.out;

import org.example.payments.core.domain.Payment;
import org.example.payments.core.domain.PaymentId;

import java.util.Optional;

public interface PaymentsRepository {
    PaymentId nextId();
    Optional<Payment> findByMerchantAndExternalId(String merchantId, String externalId);
    Payment addPayment(Payment payment);
}

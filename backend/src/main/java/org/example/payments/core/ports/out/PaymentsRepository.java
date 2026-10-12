package org.example.payments.core.ports.out;

import org.example.payments.core.domain.Payment;
import org.example.payments.core.domain.PaymentId;

import java.util.List;
import java.util.Optional;

public interface PaymentsRepository {
    PaymentId nextId();
    List<Payment> getAllPayments();
    List<Payment> getPaymentsByMerchantAndExternalId(String merchantId, String externalId);
    Optional<Payment> getPaymentById(PaymentId paymentId);
    Payment addPayment(Payment payment);
}

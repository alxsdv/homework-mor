package org.example.payments.adapters.out;

import org.example.payments.core.domain.Payment;
import org.example.payments.core.domain.PaymentId;
import org.example.payments.core.ports.out.DuplicatePaymentIdException;
import org.example.payments.core.ports.out.PaymentsRepository;
import org.example.shared.utils.PublicIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class PaymentsRepositoryImpl implements PaymentsRepository {

    private final Logger logger = LoggerFactory.getLogger(PaymentsRepositoryImpl.class);

    private final Map<PaymentId, Payment> payments = new ConcurrentHashMap<>();

    @Override
    public PaymentId nextId() {
        return new PaymentId(PublicIds.generate("PAY", 10));
    }

    @Override
    public Optional<Payment> findByMerchantAndExternalId(String merchantId, String externalId) {
        logger.info("Getting Payment by Merchant ID \"{}\" and External ID \"{}\"", merchantId, externalId);
        return payments.values().stream()
                .filter(p -> p.merchantId().equals(merchantId) && p.externalId().equals(externalId))
                .findFirst();
    }

    @Override
    public Payment addPayment(Payment payment) {
        logger.info("Adding new payment: {} to the merchant ID {}", payment, payment.merchantId());
        if (payments.putIfAbsent(payment.paymentId(), payment) != null) {
            throw new DuplicatePaymentIdException(payment.paymentId());
        }
        return payment;
    }
}

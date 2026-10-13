package org.example.payments.core.services;

import org.example.payments.core.domain.Payment;
import org.example.payments.core.domain.PaymentId;
import org.example.payments.core.ports.out.DuplicateExternalIdException;
import org.example.payments.core.ports.out.DuplicatePaymentIdException;
import org.example.payments.core.ports.out.PaymentsRepository;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** In-memory PaymentsRepository whose next addPayment calls can be scripted to fail. */
class FakePaymentsRepository implements PaymentsRepository {

    static final PaymentId CONCURRENT_WINNER_ID = new PaymentId("PAY-9999999999");

    private final Map<String, Payment> byMerchantAndExternalId = new LinkedHashMap<>();
    private final Deque<Function<Payment, RuntimeException>> addFailures = new ArrayDeque<>();
    private int sequence;
    int addCalls;

    @Override
    public PaymentId nextId() {
        sequence++;
        return new PaymentId("PAY-%010d".formatted(sequence));
    }

    @Override
    public Optional<Payment> findByMerchantAndExternalId(String merchantId, String externalId) {
        return Optional.ofNullable(byMerchantAndExternalId.get(key(merchantId, externalId)));
    }

    @Override
    public Optional<Payment> findByIdForUpdate(PaymentId paymentId) {
        return byMerchantAndExternalId.values().stream()
                .filter(payment -> payment.paymentId().equals(paymentId))
                .findFirst();
    }

    @Override
    public Payment addPayment(Payment payment) {
        addCalls++;
        Function<Payment, RuntimeException> failure = addFailures.poll();
        if (failure != null) {
            throw failure.apply(payment);
        }
        byMerchantAndExternalId.put(key(payment.merchantId(), payment.externalId()), payment);
        return payment;
    }

    /** The next addPayment fails as if the generated public ID already existed. */
    void failNextAddWithPaymentIdCollision() {
        addFailures.add(payment -> new DuplicatePaymentIdException(payment.paymentId()));
    }

    /**
     * The next addPayment fails as if a concurrent request had just committed the same payment
     * (same merchant + externalId) under CONCURRENT_WINNER_ID.
     */
    void failNextAddBecauseConcurrentRequestWon() {
        addFailures.add(payment -> {
            byMerchantAndExternalId.put(key(payment.merchantId(), payment.externalId()),
                                        withPaymentId(payment, CONCURRENT_WINNER_ID));
            return new DuplicateExternalIdException(payment.merchantId(), payment.externalId());
        });
    }

    /**
     * The next addPayment fails on the merchant + externalId unique key, but the conflicting payment
     * stays invisible to the retry, as if each retry raced yet another concurrent request.
     */
    void failNextAddWithExternalIdCollision() {
        addFailures.add(payment -> new DuplicateExternalIdException(payment.merchantId(), payment.externalId()));
    }

    List<Payment> stored() {
        return List.copyOf(byMerchantAndExternalId.values());
    }

    private static String key(String merchantId, String externalId) {
        return merchantId + "|" + externalId;
    }

    private static Payment withPaymentId(Payment p, PaymentId id) {
        return new Payment(id, p.externalId(), p.settledAt(), p.merchantId(), p.customer(), p.taxCountry(),
                           p.currency(), p.totalAmount(), p.taxRate(), p.taxAmount(), p.netAmount(), p.feeRate(),
                           p.feeAmount(), p.merchantAmount());
    }
}

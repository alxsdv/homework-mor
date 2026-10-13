package org.example.payments.core.services;

import org.example.payments.core.domain.PaymentId;
import org.example.payments.core.domain.Refund;
import org.example.payments.core.domain.RefundAmounts;
import org.example.payments.core.domain.RefundId;
import org.example.payments.core.ports.out.DuplicateRefundExternalIdException;
import org.example.payments.core.ports.out.DuplicateRefundIdException;
import org.example.payments.core.ports.out.RefundsRepository;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** In-memory RefundsRepository whose next addRefund calls can be scripted to fail. */
class FakeRefundsRepository implements RefundsRepository {

    static final RefundId CONCURRENT_WINNER_ID = new RefundId("REF-9999999999");

    private final List<Refund> refunds = new ArrayList<>();
    private final Deque<Function<Refund, RuntimeException>> addFailures = new ArrayDeque<>();
    private int sequence;
    int addCalls;

    @Override
    public RefundId nextId() {
        sequence++;
        return new RefundId("REF-%010d".formatted(sequence));
    }

    @Override
    public Optional<Refund> findByMerchantAndExternalId(String merchantId, String externalId) {
        return refunds.stream()
                .filter(refund -> refund.merchantId().equals(merchantId) && refund.externalId().equals(externalId))
                .findFirst();
    }

    @Override
    public RefundAmounts refundedSoFar(PaymentId paymentId, Currency currency) {
        RefundAmounts sum = RefundAmounts.none(currency);
        for (Refund refund : refunds) {
            if (refund.paymentId().equals(paymentId)) {
                sum = new RefundAmounts(sum.totalAmount().add(refund.totalAmount()),
                                        sum.taxAmount().add(refund.taxAmount()),
                                        sum.netAmount().add(refund.netAmount()),
                                        sum.feeAmount().add(refund.feeAmount()),
                                        sum.merchantAmount().add(refund.merchantAmount()));
            }
        }
        return sum;
    }

    @Override
    public Refund addRefund(Refund refund) {
        addCalls++;
        Function<Refund, RuntimeException> failure = addFailures.poll();
        if (failure != null) {
            throw failure.apply(refund);
        }
        refunds.add(refund);
        return refund;
    }

    /** The next addRefund fails as if the generated public ID already existed. */
    void failNextAddWithRefundIdCollision() {
        addFailures.add(refund -> new DuplicateRefundIdException(refund.refundId()));
    }

    /**
     * The next addRefund fails as if a concurrent request had just committed the same refund
     * (same merchant + externalId) under CONCURRENT_WINNER_ID.
     */
    void failNextAddBecauseConcurrentRequestWon() {
        addFailures.add(refund -> {
            refunds.add(withRefundId(refund, CONCURRENT_WINNER_ID));
            return new DuplicateRefundExternalIdException(refund.merchantId(), refund.externalId());
        });
    }

    List<Refund> stored() {
        return List.copyOf(refunds);
    }

    private static Refund withRefundId(Refund r, RefundId id) {
        return new Refund(id, r.paymentId(), r.merchantId(), r.externalId(), r.refundedAt(), r.fullRefund(),
                          r.taxCountry(), r.currency(), r.taxRate(), r.feeRate(), r.totalAmount(), r.taxAmount(),
                          r.netAmount(), r.feeAmount(), r.merchantAmount());
    }
}

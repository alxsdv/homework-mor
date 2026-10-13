package org.example.payments.core.ports.out;

import org.example.payments.core.domain.PaymentId;
import org.example.payments.core.domain.Refund;
import org.example.payments.core.domain.RefundAmounts;
import org.example.payments.core.domain.RefundId;

import java.util.Currency;
import java.util.Optional;

public interface RefundsRepository {
    RefundId nextId();

    /** Exact, case-sensitive match. */
    Optional<Refund> findByMerchantAndExternalId(String merchantId, String externalId);

    /** Sum of the payment's refunds so far, at the currency's scale; all zeros if there are none. */
    RefundAmounts refundedSoFar(PaymentId paymentId, Currency currency);

    Refund addRefund(Refund refund);
}

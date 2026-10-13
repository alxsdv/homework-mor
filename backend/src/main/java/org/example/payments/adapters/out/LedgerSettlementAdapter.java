package org.example.payments.adapters.out;

import org.example.ledger.core.domain.AccountKey;
import org.example.ledger.core.domain.LedgerEntry;
import org.example.ledger.core.domain.LedgerTransaction;
import org.example.ledger.core.ports.in.PostToLedgerUseCase;
import org.example.payments.core.domain.Payment;
import org.example.payments.core.domain.Refund;
import org.example.payments.core.ports.out.SettlementLedger;
import org.springframework.stereotype.Component;

import java.util.Currency;
import java.util.List;

/** The only place payments talks to the ledger. */
@Component
public class LedgerSettlementAdapter implements SettlementLedger {

    static final String PAYMENT_SETTLED = "PAYMENT_SETTLED";
    static final String PAYMENT_REFUNDED = "PAYMENT_REFUNDED";
    static final String PAYMENT_SOURCE_TYPE = "payment";
    static final String REFUND_SOURCE_TYPE = "refund";

    private final PostToLedgerUseCase postToLedger;

    public LedgerSettlementAdapter(PostToLedgerUseCase postToLedger) {
        this.postToLedger = postToLedger;
    }

    /** Cash in; tax, fee and merchant share owed out. */
    static LedgerTransaction toLedgerTransaction(Payment payment) {
        Currency currency = payment.currency();
        String sourceId = payment.paymentId().value();
        return new LedgerTransaction(PAYMENT_SETTLED, PAYMENT_SOURCE_TYPE, sourceId, payment.settledAt(), List.of(
                new LedgerEntry(AccountKey.cashClearing(currency), payment.totalAmount()),
                new LedgerEntry(AccountKey.taxPayable(currency, payment.taxCountry()), payment.taxAmount().negate()),
                new LedgerEntry(AccountKey.feeRevenue(currency), payment.feeAmount().negate()),
                new LedgerEntry(AccountKey.merchantPayable(currency, payment.merchantId()),
                                payment.merchantAmount().negate())));
    }

    /** The settlement's lines with the signs flipped: cash back out; tax, fee and merchant share no longer owed. */
    static LedgerTransaction toLedgerTransaction(Refund refund) {
        Currency currency = refund.currency();
        String sourceId = refund.refundId().value();
        return new LedgerTransaction(PAYMENT_REFUNDED, REFUND_SOURCE_TYPE, sourceId, refund.refundedAt(), List.of(
                new LedgerEntry(AccountKey.cashClearing(currency), refund.totalAmount().negate()),
                new LedgerEntry(AccountKey.taxPayable(currency, refund.taxCountry()), refund.taxAmount()),
                new LedgerEntry(AccountKey.feeRevenue(currency), refund.feeAmount()),
                new LedgerEntry(AccountKey.merchantPayable(currency, refund.merchantId()),
                                refund.merchantAmount())));
    }

    @Override
    public void recordSettlement(Payment payment) {
        postToLedger.post(toLedgerTransaction(payment));
    }

    @Override
    public void recordRefund(Refund refund) {
        postToLedger.post(toLedgerTransaction(refund));
    }
}

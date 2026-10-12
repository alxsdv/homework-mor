package org.example.payments.adapters.out;

import org.example.ledger.core.domain.AccountKey;
import org.example.ledger.core.domain.LedgerEntry;
import org.example.ledger.core.domain.LedgerTransaction;
import org.example.ledger.core.ports.in.PostToLedgerUseCase;
import org.example.payments.core.domain.Payment;
import org.example.payments.core.ports.out.SettlementLedger;
import org.springframework.stereotype.Component;

import java.util.Currency;
import java.util.List;

/** The only place payments talks to the ledger. */
@Component
public class LedgerSettlementAdapter implements SettlementLedger {

    static final String PAYMENT_SETTLED = "PAYMENT_SETTLED";
    static final String SOURCE_TYPE = "payment";

    private final PostToLedgerUseCase postToLedger;

    public LedgerSettlementAdapter(PostToLedgerUseCase postToLedger) {
        this.postToLedger = postToLedger;
    }

    /** Cash in; tax, fee and merchant share owed out. */
    static LedgerTransaction toLedgerTransaction(Payment payment) {
        Currency currency = payment.currency();
        String sourceId = payment.paymentId().value();
        return new LedgerTransaction(PAYMENT_SETTLED, SOURCE_TYPE, sourceId, payment.settledAt(), List.of(
                new LedgerEntry(AccountKey.cashClearing(currency), payment.totalAmount()),
                new LedgerEntry(AccountKey.taxPayable(currency, payment.taxCountry()), payment.taxAmount().negate()),
                new LedgerEntry(AccountKey.feeRevenue(currency), payment.feeAmount().negate()),
                new LedgerEntry(AccountKey.merchantPayable(currency, payment.merchantId()),
                                payment.merchantAmount().negate())));
    }

    @Override
    public void recordSettlement(Payment payment) {
        postToLedger.post(toLedgerTransaction(payment));
    }
}

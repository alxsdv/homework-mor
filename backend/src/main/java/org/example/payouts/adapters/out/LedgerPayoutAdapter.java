package org.example.payouts.adapters.out;

import org.example.ledger.core.domain.AccountKey;
import org.example.ledger.core.domain.LedgerEntry;
import org.example.ledger.core.domain.LedgerTransaction;
import org.example.ledger.core.ports.in.InsufficientFundsException;
import org.example.ledger.core.ports.in.PostToLedgerUseCase;
import org.example.payouts.core.domain.Payout;
import org.example.payouts.core.ports.out.InsufficientAvailableBalanceException;
import org.example.payouts.core.ports.out.PayoutLedger;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * The only place payouts talks to the ledger. A payout moves money between three accounts: the merchant's
 * available balance (MERCHANT_PAYABLE), what is held for their payouts (MERCHANT_PAYOUT_PENDING), and cash.
 */
@Component
public class LedgerPayoutAdapter implements PayoutLedger {

    static final String PAYOUT_HELD = "PAYOUT_HELD";
    static final String PAYOUT_SETTLED = "PAYOUT_SETTLED";
    static final String PAYOUT_RELEASED = "PAYOUT_RELEASED";
    static final String PAYOUT_SOURCE_TYPE = "payout";

    private final PostToLedgerUseCase postToLedger;

    public LedgerPayoutAdapter(PostToLedgerUseCase postToLedger) {
        this.postToLedger = postToLedger;
    }

    private static LedgerTransaction transaction(String type, Payout payout, Instant effectiveAt,
                                                 AccountKey debited, AccountKey credited) {
        return new LedgerTransaction(type, PAYOUT_SOURCE_TYPE, payout.payoutId().value(), effectiveAt, List.of(
                new LedgerEntry(debited, payout.amount()),
                new LedgerEntry(credited, payout.amount().negate())));
    }

    private static AccountKey available(Payout payout) {
        return AccountKey.merchantPayable(payout.currency(), payout.merchantId());
    }

    private static AccountKey pending(Payout payout) {
        return AccountKey.merchantPayoutPending(payout.currency(), payout.merchantId());
    }

    private static Instant completedAt(Payout payout) {
        return Objects.requireNonNull(payout.completedAt(), "a payout that has ended has a completedAt");
    }

    /** We owe the merchant as much as before, but it's no longer available to pay out again. Cash doesn't move. */
    static LedgerTransaction toHeld(Payout payout) {
        return transaction(PAYOUT_HELD, payout, payout.requestedAt(), available(payout), pending(payout));
    }

    /** We no longer owe the merchant the held amount, and the money left our bank account. */
    static LedgerTransaction toSettled(Payout payout) {
        return transaction(PAYOUT_SETTLED, payout, completedAt(payout), pending(payout),
                           AccountKey.cashClearing(payout.currency()));
    }

    /** The held amount is available to the merchant again. Cash never moved. */
    static LedgerTransaction toReleased(Payout payout) {
        return transaction(PAYOUT_RELEASED, payout, completedAt(payout), pending(payout), available(payout));
    }

    @Override
    public void hold(Payout payout) {
        try {
            postToLedger.postWithinAvailableFunds(toHeld(payout));
        } catch (InsufficientFundsException e) {
            Currency currency = payout.currency();
            BigDecimal available = e.available().setScale(currency.getDefaultFractionDigits(),
                                                          RoundingMode.UNNECESSARY);
            throw new InsufficientAvailableBalanceException(available);
        }
    }

    @Override
    public void settle(Payout payout) {
        postToLedger.post(toSettled(payout));
    }

    @Override
    public void release(Payout payout) {
        postToLedger.post(toReleased(payout));
    }
}

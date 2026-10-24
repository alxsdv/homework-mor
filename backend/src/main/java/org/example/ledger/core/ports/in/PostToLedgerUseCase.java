package org.example.ledger.core.ports.in;

import org.example.ledger.core.domain.LedgerTransaction;

public interface PostToLedgerUseCase {

    /**
     * Records a balanced ledger transaction. Joins the caller's database transaction if there is one, otherwise
     * runs in its own. Throws Spring's DuplicateKeyException if a ledger transaction with the same type + source
     * already exists.
     */
    void post(LedgerTransaction transaction);

    /**
     * Like post, for a transaction that takes money out of a merchant's available balance (MERCHANT_PAYABLE),
     * such as a payout hold. Under the account's row lock it checks that the balance covers the debit, so the
     * merchant can't end up owing us. Throws InsufficientFundsException otherwise, before anything is written.
     */
    void postWithinAvailableFunds(LedgerTransaction transaction);
}

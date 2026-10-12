package org.example.ledger.core.ports.out;

import org.example.ledger.core.domain.AccountKey;
import org.example.ledger.core.domain.LedgerTransaction;

import java.math.BigDecimal;
import java.util.List;

public interface LedgerRepository {

    /** Returns the account's id, creating it (MERCHANT_PAYABLE with balance 0) if it doesn't exist yet. */
    long findOrCreateAccount(AccountKey key);

    /**
     * SELECT balance … FOR UPDATE: the one place a ledger row lock is taken on purpose.
     * The lock is held until the surrounding transaction commits or rolls back.
     */
    BigDecimal lockCachedAccount(long accountId);

    /** Returns the new transaction's id. */
    long insertTransaction(LedgerTransaction transaction);

    void insertEntries(long transactionId, List<NewEntry> entries);

    void updateBalance(long accountId, BigDecimal newBalance);
}

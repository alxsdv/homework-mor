package org.example.ledger.core.ports.in;

import org.example.ledger.core.domain.LedgerTransaction;

public interface PostToLedgerUseCase {

    /**
     * Records a balanced ledger transaction. Joins the caller's database transaction if there is one, otherwise
     * runs in its own. Throws Spring's DuplicateKeyException if a ledger transaction with the same type + source
     * already exists.
     */
    void post(LedgerTransaction transaction);
}

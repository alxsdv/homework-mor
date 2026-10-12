package org.example.shared.transactions;

import java.util.function.Supplier;

/**
 * Runs work in a database transaction: joins the current one if there is one, otherwise starts and commits
 * a new one. Shared by the payments and ledger modules so a ledger posting joins the payment's transaction.
 */
public interface TransactionRunner {
    <T> T inTransaction(Supplier<T> work);
    void inTransaction(Runnable work);
}

package org.example.payments.core.services;

import org.example.shared.transactions.TransactionRunner;

import java.util.function.Supplier;

/** Runs the work immediately, without a database transaction. */
class DirectTransactionRunner implements TransactionRunner {

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return work.get();
    }

    @Override
    public void inTransaction(Runnable work) {
        work.run();
    }
}

package org.example.payments.adapters.out;

import org.example.payments.core.ports.out.TransactionRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
public class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate transactionTemplate;

    public SpringTransactionRunner(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(t -> work.get());
    }

    @Override
    public void inTransaction(Runnable work) {
        transactionTemplate.executeWithoutResult(t -> work.run());
    }
}

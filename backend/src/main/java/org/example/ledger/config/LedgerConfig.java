package org.example.ledger.config;

import org.example.ledger.core.ports.in.PostToLedgerUseCase;
import org.example.ledger.core.ports.out.LedgerRepository;
import org.example.ledger.core.services.PostToLedgerUseCaseImpl;
import org.example.shared.transactions.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LedgerConfig {

    @Bean
    PostToLedgerUseCase postToLedgerUseCase(LedgerRepository ledgerRepository,
                                            TransactionRunner transactionRunner) {
        return new PostToLedgerUseCaseImpl(ledgerRepository, transactionRunner);
    }
}

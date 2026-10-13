package org.example.ledger.config;

import org.example.ledger.core.ports.in.GetMerchantPayablesUseCase;
import org.example.ledger.core.ports.in.GetTaxPayablesUseCase;
import org.example.ledger.core.ports.in.PostToLedgerUseCase;
import org.example.ledger.core.ports.out.LedgerQueries;
import org.example.ledger.core.ports.out.LedgerRepository;
import org.example.ledger.core.services.GetMerchantPayablesUseCaseImpl;
import org.example.ledger.core.services.GetTaxPayablesUseCaseImpl;
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

    @Bean
    GetMerchantPayablesUseCase getMerchantPayablesUseCase(LedgerQueries ledgerQueries) {
        return new GetMerchantPayablesUseCaseImpl(ledgerQueries);
    }

    @Bean
    GetTaxPayablesUseCase getTaxPayablesUseCase(LedgerQueries ledgerQueries) {
        return new GetTaxPayablesUseCaseImpl(ledgerQueries);
    }
}

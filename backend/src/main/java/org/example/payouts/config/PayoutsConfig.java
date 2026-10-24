package org.example.payouts.config;

import org.example.payouts.core.ports.in.CompletePayoutUseCase;
import org.example.payouts.core.ports.in.RequestPayoutUseCase;
import org.example.payouts.core.ports.out.MerchantDirectory;
import org.example.payouts.core.ports.out.PayoutLedger;
import org.example.payouts.core.ports.out.PayoutsRepository;
import org.example.payouts.core.services.CompletePayoutUseCaseImpl;
import org.example.payouts.core.services.RequestPayoutUseCaseImpl;
import org.example.shared.transactions.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PayoutsConfig {

    @Bean
    RequestPayoutUseCase requestPayoutUseCase(PayoutsRepository payoutsRepository,
                                              MerchantDirectory merchantDirectory,
                                              PayoutLedger payoutLedger,
                                              TransactionRunner transactionRunner) {
        return new RequestPayoutUseCaseImpl(payoutsRepository, merchantDirectory, payoutLedger, transactionRunner);
    }

    @Bean
    CompletePayoutUseCase completePayoutUseCase(PayoutsRepository payoutsRepository,
                                                PayoutLedger payoutLedger,
                                                TransactionRunner transactionRunner) {
        return new CompletePayoutUseCaseImpl(payoutsRepository, payoutLedger, transactionRunner);
    }
}

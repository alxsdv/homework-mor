package org.example.payments.config;

import org.example.payments.core.ports.in.RecordSettledPaymentUseCase;
import org.example.payments.core.ports.out.MerchantsRepository;
import org.example.payments.core.ports.out.PaymentsRepository;
import org.example.payments.core.ports.out.SettlementLedger;
import org.example.shared.transactions.TransactionRunner;
import org.example.payments.core.services.RecordSettledPaymentUseCaseImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentsConfig {

    @Bean
    RecordSettledPaymentUseCase recordSettledPaymentUseCase(PaymentsRepository paymentsRepository,
                                                            MerchantsRepository merchantsRepository,
                                                            SettlementLedger settlementLedger,
                                                            TransactionRunner transactionRunner) {
        return new RecordSettledPaymentUseCaseImpl(paymentsRepository, merchantsRepository, settlementLedger,
                                                   transactionRunner);
    }
}

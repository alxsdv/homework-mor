package org.example.payments.config;

import org.example.payments.core.ports.in.RecordSettledPaymentUseCase;
import org.example.payments.core.ports.out.PaymentsRepository;
import org.example.payments.core.ports.out.TransactionRunner;
import org.example.payments.core.services.RecordSettledPaymentUseCaseImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentsConfig {

    @Bean
    RecordSettledPaymentUseCase recordSettledPaymentUseCase(PaymentsRepository paymentsRepository,
                                                            TransactionRunner transactionRunner) {
        return new RecordSettledPaymentUseCaseImpl(paymentsRepository, transactionRunner);
    }
}

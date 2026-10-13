package org.example.payments.config;

import org.example.payments.core.ports.in.RecordRefundUseCase;
import org.example.payments.core.ports.in.RecordSettledPaymentUseCase;
import org.example.payments.core.ports.out.MerchantLookup;
import org.example.payments.core.ports.out.PaymentsRepository;
import org.example.payments.core.ports.out.RefundsRepository;
import org.example.payments.core.ports.out.SettlementLedger;
import org.example.payments.core.services.RecordRefundUseCaseImpl;
import org.example.payments.core.services.RecordSettledPaymentUseCaseImpl;
import org.example.shared.transactions.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentsConfig {

    @Bean
    RecordSettledPaymentUseCase recordSettledPaymentUseCase(PaymentsRepository paymentsRepository,
                                                            MerchantLookup merchantLookup,
                                                            SettlementLedger settlementLedger,
                                                            TransactionRunner transactionRunner) {
        return new RecordSettledPaymentUseCaseImpl(paymentsRepository, merchantLookup, settlementLedger,
                                                   transactionRunner);
    }

    @Bean
    RecordRefundUseCase recordRefundUseCase(PaymentsRepository paymentsRepository,
                                            RefundsRepository refundsRepository,
                                            SettlementLedger settlementLedger,
                                            TransactionRunner transactionRunner) {
        return new RecordRefundUseCaseImpl(paymentsRepository, refundsRepository, settlementLedger,
                                           transactionRunner);
    }
}

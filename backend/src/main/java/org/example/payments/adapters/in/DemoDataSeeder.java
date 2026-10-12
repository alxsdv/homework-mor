package org.example.payments.adapters.in;

import org.example.payments.core.domain.Payment;
import org.example.payments.core.ports.in.RecordSettledPaymentCommand;
import org.example.payments.core.ports.in.RecordSettledPaymentUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds demo payments through the real use case, so each gets its ledger posting.
 * Any failure propagates and stops startup: half-seeded data would be misleading.
 */
@Component
@Profile("dev")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final RecordSettledPaymentUseCase recordSettledPaymentUseCase;

    public DemoDataSeeder(RecordSettledPaymentUseCase recordSettledPaymentUseCase) {
        this.recordSettledPaymentUseCase = recordSettledPaymentUseCase;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<RecordSettledPaymentCommand> commands = DemoPayments.commands();
        for (RecordSettledPaymentCommand command : commands) {
            Payment payment = recordSettledPaymentUseCase.recordSettledPayment(command);
            logger.info("Demo payment {} for {} is {}", command.externalId(), command.merchantId(),
                        payment.paymentId().value());
        }
        logger.info("Seeded {} demo payments", commands.size());
    }
}

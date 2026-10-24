package org.example.payouts.core.services;

import org.example.payouts.core.domain.Payout;
import org.example.payouts.core.domain.PayoutStatus;
import org.example.payouts.core.ports.in.CompletePayoutCommand;
import org.example.payouts.core.ports.in.CompletePayoutUseCase;
import org.example.payouts.core.ports.in.InvalidPayoutException;
import org.example.payouts.core.ports.in.PayoutAlreadyCompletedException;
import org.example.payouts.core.ports.in.PayoutNotFoundException;
import org.example.payouts.core.ports.out.PayoutLedger;
import org.example.payouts.core.ports.out.PayoutsRepository;
import org.example.shared.transactions.TransactionRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompletePayoutUseCaseImpl implements CompletePayoutUseCase {

    private final Logger logger = LoggerFactory.getLogger(CompletePayoutUseCaseImpl.class);
    private final PayoutsRepository payoutsRepository;
    private final PayoutLedger payoutLedger;
    private final TransactionRunner transactionRunner;

    public CompletePayoutUseCaseImpl(PayoutsRepository payoutsRepository, PayoutLedger payoutLedger,
                                     TransactionRunner transactionRunner) {
        this.payoutsRepository = payoutsRepository;
        this.payoutLedger = payoutLedger;
        this.transactionRunner = transactionRunner;
    }

    @Override
    public Payout settle(CompletePayoutCommand command) {
        logger.info("Settle payout {} at {}", command.payoutId().value(), command.completedAt());
        return transactionRunner.inTransaction(() -> complete(command, PayoutStatus.SETTLED));
    }

    @Override
    public Payout fail(CompletePayoutCommand command) {
        logger.info("Fail payout {} at {}", command.payoutId().value(), command.completedAt());
        return transactionRunner.inTransaction(() -> complete(command, PayoutStatus.FAILED));
    }

    private Payout complete(CompletePayoutCommand command, PayoutStatus outcome) {
        // The row lock makes a concurrent settle and fail of this payout take turns: the second one sees it ended.
        Payout payout = payoutsRepository.findByIdForUpdate(command.payoutId())
                .orElseThrow(() -> new PayoutNotFoundException(command.payoutId().value()));

        if (payout.status() != PayoutStatus.PENDING) {
            if (payout.status() == outcome && command.completedAt().equals(payout.completedAt())) {
                return payout;
            }
            throw new PayoutAlreadyCompletedException(payout);
        }
        if (command.completedAt().isBefore(payout.requestedAt())) {
            throw new InvalidPayoutException("completedAt must not be before the payout's requestedAt "
                                             + payout.requestedAt() + ": " + command.completedAt());
        }

        Payout completed = payout.complete(outcome, command.completedAt());
        payoutsRepository.updateCompleted(completed);
        if (outcome == PayoutStatus.SETTLED) {
            payoutLedger.settle(completed);
        } else {
            payoutLedger.release(completed);
        }
        return completed;
    }
}

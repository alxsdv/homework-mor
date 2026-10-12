package org.example.payments.core.services;

import org.example.payments.core.domain.Payment;
import org.example.payments.core.ports.in.PaymentConflictException;
import org.example.payments.core.ports.in.UnknownMerchantException;
import org.example.payments.core.ports.out.DuplicatePaymentIdException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.example.payments.core.services.TestCommands.command;

class RecordSettledPaymentUseCaseImplTest {

    private final FakePaymentsRepository payments = new FakePaymentsRepository();
    private final FakeMerchantsRepository merchants =
            new FakeMerchantsRepository(Map.of("MERCH-001", new BigDecimal("0.03")));
    private final RecordSettledPaymentUseCaseImpl useCase =
            new RecordSettledPaymentUseCaseImpl(payments, merchants, new DirectTransactionRunner());

    @Test
    void recordsNewPaymentWithCalculatedAmounts() {
        Payment payment = useCase.recordSettledPayment(command("EXT-1"));

        assertThat(payment.totalAmount()).isEqualByComparingTo("119.00");
        assertThat(payment.taxAmount()).isEqualByComparingTo("19.00");
        assertThat(payment.netAmount()).isEqualByComparingTo("100.00");
        assertThat(payment.feeRate()).isEqualByComparingTo("0.03");
        assertThat(payment.feeAmount()).isEqualByComparingTo("3.00");
        assertThat(payment.merchantAmount()).isEqualByComparingTo("97.00");
        assertThat(payment.taxCountry()).isEqualTo("DE");
        assertThat(payment.settledAt()).isEqualTo(Instant.parse("2026-09-30T21:30:00Z"));
        assertThat(payments.stored()).containsExactly(payment);
    }

    @Test
    void returnsExistingPaymentForMatchingRepeat() {
        Payment first = useCase.recordSettledPayment(command("EXT-1"));

        Payment second = useCase.recordSettledPayment(command("EXT-1"));

        assertThat(second).isEqualTo(first);
        assertThat(payments.addCalls).isEqualTo(1);
    }

    @Test
    void returnsExistingPaymentForEquivalentRepeat() {
        Payment first = useCase.recordSettledPayment(command("EXT-1", "MERCH-001", "119.00", "EUR",
                                                             "2026-09-30T21:30:00Z"));

        // Same amount with a different scale, same instant written with a different offset.
        Payment second = useCase.recordSettledPayment(command("EXT-1", "MERCH-001", "119.0", "EUR",
                                                              "2026-09-30T23:30:00+02:00"));

        assertThat(second.paymentId()).isEqualTo(first.paymentId());
        assertThat(payments.addCalls).isEqualTo(1);
    }

    @Test
    void conflictWhenRepeatHasDifferentAmount() {
        useCase.recordSettledPayment(command("EXT-1", "MERCH-001", "119.00", "EUR", "2026-09-30T21:30:00Z"));

        assertThatThrownBy(() -> useCase.recordSettledPayment(
                command("EXT-1", "MERCH-001", "120.00", "EUR", "2026-09-30T21:30:00Z")))
                .isInstanceOf(PaymentConflictException.class);
    }

    @Test
    void conflictWhenRepeatHasDifferentCurrency() {
        useCase.recordSettledPayment(command("EXT-1", "MERCH-001", "119.00", "EUR", "2026-09-30T21:30:00Z"));

        assertThatThrownBy(() -> useCase.recordSettledPayment(
                command("EXT-1", "MERCH-001", "119.00", "USD", "2026-09-30T21:30:00Z")))
                .isInstanceOf(PaymentConflictException.class);
    }

    @Test
    void conflictWhenRepeatHasDifferentSettledAt() {
        useCase.recordSettledPayment(command("EXT-1", "MERCH-001", "119.00", "EUR", "2026-09-30T21:30:00Z"));

        assertThatThrownBy(() -> useCase.recordSettledPayment(
                command("EXT-1", "MERCH-001", "119.00", "EUR", "2026-09-30T21:30:00.000001Z")))
                .isInstanceOf(PaymentConflictException.class);
    }

    @Test
    void rejectsUnknownMerchant() {
        assertThatThrownBy(() -> useCase.recordSettledPayment(
                command("EXT-1", "MERCH-999", "119.00", "EUR", "2026-09-30T21:30:00Z")))
                .isInstanceOf(UnknownMerchantException.class)
                .hasMessageContaining("MERCH-999");
        assertThat(payments.stored()).isEmpty();
    }

    @Test
    void matchesMerchantIdExactly() {
        assertThatThrownBy(() -> useCase.recordSettledPayment(
                command("EXT-1", "merch-001", "119.00", "EUR", "2026-09-30T21:30:00Z")))
                .isInstanceOf(UnknownMerchantException.class);
    }

    @Test
    void returnsConcurrentWinnersPaymentAfterDuplicateExternalId() {
        payments.failNextAddBecauseConcurrentRequestWon();

        Payment payment = useCase.recordSettledPayment(command("EXT-1"));

        assertThat(payment.paymentId()).isEqualTo(FakePaymentsRepository.CONCURRENT_WINNER_ID);
        assertThat(payments.addCalls).isEqualTo(1);
    }

    @Test
    void retriesPaymentIdCollision() {
        payments.failNextAddWithPaymentIdCollision();

        Payment payment = useCase.recordSettledPayment(command("EXT-1"));

        assertThat(payments.stored()).containsExactly(payment);
        assertThat(payments.addCalls).isEqualTo(2);
    }

    @Test
    void givesUpAfterThreePaymentIdCollisions() {
        payments.failNextAddWithPaymentIdCollision();
        payments.failNextAddWithPaymentIdCollision();
        payments.failNextAddWithPaymentIdCollision();

        assertThatThrownBy(() -> useCase.recordSettledPayment(command("EXT-1")))
                .isInstanceOf(DuplicatePaymentIdException.class);
        assertThat(payments.addCalls).isEqualTo(3);
    }
}

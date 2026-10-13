package org.example.payments.core.services;

import org.example.payments.core.domain.PaymentId;
import org.example.payments.core.domain.Refund;
import org.example.payments.core.ports.in.InvalidPaymentException;
import org.example.payments.core.ports.in.PaymentNotFoundException;
import org.example.payments.core.ports.in.RecordRefundCommand;
import org.example.payments.core.ports.in.RefundConflictException;
import org.example.payments.core.ports.in.RefundExceedsRefundableException;
import org.example.payments.core.ports.out.DuplicateRefundExternalIdException;
import org.example.payments.core.ports.out.DuplicateRefundIdException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordRefundUseCaseImplTest {

    private static final String PAYMENT_ID = "PAY-0000000001";
    private static final String REFUNDED_AT = "2026-10-02T08:15:00Z";

    private final FakePaymentsRepository payments = new FakePaymentsRepository();
    private final FakeRefundsRepository refunds = new FakeRefundsRepository();
    private final FakeSettlementLedger ledger = new FakeSettlementLedger();
    private final RecordRefundUseCaseImpl useCase =
            new RecordRefundUseCaseImpl(payments, refunds, ledger, new DirectTransactionRunner());

    /** 100.00 EUR at 19% tax and a 3% fee: tax 15.97, net 84.03, fee 2.52, merchant 81.51. */
    @BeforeEach
    void storePayment() {
        payments.addPayment(TestPayments.payment(PAYMENT_ID, "EXT-1", "MERCH-001", "100.00"));
    }

    /** A null refundAmount is a full refund. */
    private static RecordRefundCommand command(String paymentId, String externalId, String refundedAt,
                                               String refundAmount) {
        return new RecordRefundCommand(new PaymentId(paymentId), externalId, Instant.parse(refundedAt),
                                       refundAmount == null ? null : new BigDecimal(refundAmount));
    }

    private static RecordRefundCommand command(String externalId, String refundAmount) {
        return command(PAYMENT_ID, externalId, REFUNDED_AT, refundAmount);
    }

    @Test
    void recordsNewRefundWithItsSplitAndThePaymentsSnapshot() {
        Refund refund = useCase.recordRefund(command("RE-1", "25.00"));

        assertThat(refund.paymentId()).isEqualTo(new PaymentId(PAYMENT_ID));
        assertThat(refund.merchantId()).isEqualTo("MERCH-001");
        assertThat(refund.externalId()).isEqualTo("RE-1");
        assertThat(refund.refundedAt()).isEqualTo(Instant.parse(REFUNDED_AT));
        assertThat(refund.fullRefund()).isFalse();
        assertThat(refund.taxCountry()).isEqualTo("DE");
        assertThat(refund.currency()).isEqualTo(Currency.getInstance("EUR"));
        assertThat(refund.taxRate()).isEqualByComparingTo("0.19");
        assertThat(refund.feeRate()).isEqualByComparingTo("0.03");
        assertThat(refund.totalAmount()).isEqualTo(new BigDecimal("25.00"));
        assertThat(refund.taxAmount()).isEqualTo(new BigDecimal("3.99"));
        assertThat(refund.netAmount()).isEqualTo(new BigDecimal("21.01"));
        assertThat(refund.feeAmount()).isEqualTo(new BigDecimal("0.63"));
        assertThat(refund.merchantAmount()).isEqualTo(new BigDecimal("20.38"));
        assertThat(refunds.stored()).containsExactly(refund);
    }

    @Test
    void splitsLaterRefundsCumulativelyAndRefundsTheRestExactly() {
        useCase.recordRefund(command("RE-1", "25.00"));
        useCase.recordRefund(command("RE-2", "30.00"));

        Refund rest = useCase.recordRefund(command("RE-3", null));

        assertThat(rest.fullRefund()).isTrue();
        assertThat(rest.totalAmount()).isEqualTo(new BigDecimal("45.00"));
        assertThat(rest.taxAmount()).isEqualTo(new BigDecimal("7.19"));
        assertThat(rest.netAmount()).isEqualTo(new BigDecimal("37.81"));
        assertThat(rest.feeAmount()).isEqualTo(new BigDecimal("1.13"));
        assertThat(rest.merchantAmount()).isEqualTo(new BigDecimal("36.68"));
    }

    @Test
    void absentAmountRefundsWhatIsLeft() {
        Refund refund = useCase.recordRefund(command("RE-1", null));

        assertThat(refund.totalAmount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(refund.taxAmount()).isEqualTo(new BigDecimal("15.97"));
        assertThat(refund.feeAmount()).isEqualTo(new BigDecimal("2.52"));
        assertThat(refund.merchantAmount()).isEqualTo(new BigDecimal("81.51"));
    }

    @Test
    void returnsExistingRefundForMatchingRepeat() {
        Refund first = useCase.recordRefund(command("RE-1", "25.00"));

        Refund second = useCase.recordRefund(command("RE-1", "25.00"));

        assertThat(second).isEqualTo(first);
        assertThat(refunds.addCalls).isEqualTo(1);
    }

    @Test
    void returnsExistingRefundForEquivalentRepeat() {
        Refund first = useCase.recordRefund(command(PAYMENT_ID, "RE-1", "2026-10-02T08:15:00Z", "25.00"));

        // Same amount with a different scale, same instant written with a different offset.
        Refund second = useCase.recordRefund(command(PAYMENT_ID, "RE-1", "2026-10-02T10:15:00+02:00", "25"));

        assertThat(second).isEqualTo(first);
        assertThat(refunds.addCalls).isEqualTo(1);
    }

    @Test
    void repeatOfAFullRefundIsReturnedThoughNothingIsLeft() {
        Refund first = useCase.recordRefund(command("RE-1", null));

        assertThat(useCase.recordRefund(command("RE-1", null))).isEqualTo(first);
    }

    @Test
    void conflictWhenRepeatDiffers() {
        payments.addPayment(TestPayments.payment("PAY-0000000002", "EXT-2", "MERCH-001", "100.00"));
        useCase.recordRefund(command("RE-1", "25.00"));

        assertThatThrownBy(() -> useCase.recordRefund(command("PAY-0000000002", "RE-1", REFUNDED_AT, "25.00")))
                .as("another payment").isInstanceOf(RefundConflictException.class);
        assertThatThrownBy(() -> useCase.recordRefund(command(PAYMENT_ID, "RE-1", "2026-10-02T08:15:00.000001Z", "25.00")))
                .as("another refundedAt").isInstanceOf(RefundConflictException.class);
        assertThatThrownBy(() -> useCase.recordRefund(command("RE-1", "25.01")))
                .as("another amount").isInstanceOf(RefundConflictException.class);
        assertThatThrownBy(() -> useCase.recordRefund(command("RE-1", null)))
                .as("no amount after a partial refund").isInstanceOf(RefundConflictException.class);
        assertThat(refunds.addCalls).isEqualTo(1);
    }

    @Test
    void conflictWhenAnAmountRepeatsAFullRefund() {
        useCase.recordRefund(command("RE-1", null));

        assertThatThrownBy(() -> useCase.recordRefund(command("RE-1", "100.00")))
                .isInstanceOf(RefundConflictException.class)
                .hasMessageContaining("RE-1");
    }

    @Test
    void rejectsMoreThanIsLeft() {
        assertThatThrownBy(() -> useCase.recordRefund(command("RE-1", "100.01")))
                .isInstanceOf(RefundExceedsRefundableException.class)
                .hasMessageContaining("exceeds the refundable amount 100.00");

        useCase.recordRefund(command("RE-2", "60.00"));

        assertThatThrownBy(() -> useCase.recordRefund(command("RE-3", "40.01")))
                .isInstanceOf(RefundExceedsRefundableException.class)
                .hasMessageContaining("exceeds the refundable amount 40.00");
    }

    @Test
    void rejectsAnyNewRefundOnceFullyRefunded() {
        useCase.recordRefund(command("RE-1", null));

        assertThatThrownBy(() -> useCase.recordRefund(command("RE-2", null)))
                .isInstanceOf(RefundExceedsRefundableException.class)
                .hasMessageContaining("has nothing left to refund");
        assertThatThrownBy(() -> useCase.recordRefund(command("RE-3", "0.01")))
                .isInstanceOf(RefundExceedsRefundableException.class)
                .hasMessageContaining("has nothing left to refund");
    }

    @Test
    void zeroPaymentHasNothingToRefund() {
        payments.addPayment(TestPayments.payment("PAY-0000000002", "EXT-2", "MERCH-001", "0.00"));

        assertThatThrownBy(() -> useCase.recordRefund(command("PAY-0000000002", "RE-1", REFUNDED_AT, null)))
                .isInstanceOf(RefundExceedsRefundableException.class)
                .hasMessageContaining("has nothing left to refund");
    }

    @Test
    void acceptsRefundAtTheSettlementInstant() {
        Refund refund = useCase.recordRefund(command(PAYMENT_ID, "RE-1", "2026-09-30T21:30:00Z", "10.00"));

        assertThat(refund.refundedAt()).isEqualTo(Instant.parse("2026-09-30T21:30:00Z"));
    }

    @Test
    void rejectsRefundBeforeTheSettlement() {
        assertThatThrownBy(() -> useCase.recordRefund(
                command(PAYMENT_ID, "RE-1", "2026-09-30T21:29:59.999999Z", "10.00")))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessageContaining("refundedAt must not be before the payment's settledAt");
    }

    @Test
    void acceptsTrailingZerosBeyondTheCurrencyScale() {
        Refund refund = useCase.recordRefund(command("RE-1", "25.000"));

        assertThat(refund.totalAmount()).isEqualTo(new BigDecimal("25.00"));
        assertThatThrownBy(() -> useCase.recordRefund(command("RE-2", "25.001")))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessageContaining("refundAmount has more decimal places than EUR allows");
    }

    @Test
    void unknownPaymentIsNotFound() {
        assertThatThrownBy(() -> useCase.recordRefund(command("PAY-0000000099", "RE-1", REFUNDED_AT, "10.00")))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("PAY-0000000099");
    }

    @Test
    void sameExternalIdForAnotherMerchantIsANewRefund() {
        payments.addPayment(TestPayments.payment("PAY-0000000002", "EXT-1", "MERCH-002", "100.00"));

        Refund first = useCase.recordRefund(command("RE-1", "25.00"));
        Refund second = useCase.recordRefund(command("PAY-0000000002", "RE-1", REFUNDED_AT, "25.00"));

        assertThat(second.refundId()).isNotEqualTo(first.refundId());
        assertThat(refunds.stored()).containsExactly(first, second);
    }

    @Test
    void returnsConcurrentWinnersRefundAfterDuplicateExternalId() {
        refunds.failNextAddBecauseConcurrentRequestWon();

        Refund refund = useCase.recordRefund(command("RE-1", "25.00"));

        assertThat(refund.refundId()).isEqualTo(FakeRefundsRepository.CONCURRENT_WINNER_ID);
        assertThat(refunds.addCalls).isEqualTo(1);
    }

    @Test
    void retriesRefundIdCollision() {
        refunds.failNextAddWithRefundIdCollision();

        Refund refund = useCase.recordRefund(command("RE-1", "25.00"));

        assertThat(refunds.stored()).containsExactly(refund);
        assertThat(refunds.addCalls).isEqualTo(2);
    }

    @Test
    void givesUpAfterThreeRefundIdCollisions() {
        refunds.failNextAddWithRefundIdCollision();
        refunds.failNextAddWithRefundIdCollision();
        refunds.failNextAddWithRefundIdCollision();

        assertThatThrownBy(() -> useCase.recordRefund(command("RE-1", "25.00")))
                .isInstanceOf(DuplicateRefundIdException.class);
        assertThat(refunds.addCalls).isEqualTo(3);
    }

    @Test
    void givesUpAfterThreeExternalIdCollisions() {
        refunds.failNextAddWithExternalIdCollision();
        refunds.failNextAddWithExternalIdCollision();
        refunds.failNextAddWithExternalIdCollision();

        assertThatThrownBy(() -> useCase.recordRefund(command("RE-1", "25.00")))
                .isInstanceOf(DuplicateRefundExternalIdException.class);
        assertThat(refunds.addCalls).isEqualTo(3);
        assertThat(refunds.stored()).isEmpty();
        assertThat(ledger.refunds).isEmpty();
    }

    @Test
    void postsNewRefundToTheLedgerOnce() {
        Refund refund = useCase.recordRefund(command("RE-1", "25.00"));

        assertThat(ledger.refunds).containsExactly(refund);
    }

    @Test
    void doesNotPostRepeatsOrRejections() {
        useCase.recordRefund(command("RE-1", "25.00"));
        ledger.refunds.clear();

        useCase.recordRefund(command("RE-1", "25.00"));
        assertThatThrownBy(() -> useCase.recordRefund(command("RE-1", "30.00")))
                .isInstanceOf(RefundConflictException.class);
        assertThatThrownBy(() -> useCase.recordRefund(command("RE-2", "100.00")))
                .isInstanceOf(RefundExceedsRefundableException.class);
        assertThatThrownBy(() -> useCase.recordRefund(command("PAY-0000000099", "RE-3", REFUNDED_AT, "1.00")))
                .isInstanceOf(PaymentNotFoundException.class);
        assertThatThrownBy(() -> useCase.recordRefund(command(PAYMENT_ID, "RE-4", "2026-09-01T00:00:00Z", "1.00")))
                .isInstanceOf(InvalidPaymentException.class);

        assertThat(ledger.refunds).isEmpty();
    }

    @Test
    void doesNotPostWhenAConcurrentRequestWon() {
        refunds.failNextAddBecauseConcurrentRequestWon();

        useCase.recordRefund(command("RE-1", "25.00"));

        // The winning request posted its own refund.
        assertThat(ledger.refunds).isEmpty();
    }
}

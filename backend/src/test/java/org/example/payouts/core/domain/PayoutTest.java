package org.example.payouts.core.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayoutTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-10-03T07:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-10-05T08:00:00Z");

    private static Payout pending() {
        return Payout.requested(new PayoutId("PO-0123456789"), "MERCH-001", "EXT-1", Currency.getInstance("EUR"),
                                new BigDecimal("48.50"), REQUESTED_AT);
    }

    @Test
    void requestedPayoutIsPendingWithoutCompletedAt() {
        assertThat(pending().status()).isEqualTo(PayoutStatus.PENDING);
        assertThat(pending().completedAt()).isNull();
    }

    @Test
    void completingSetsOutcomeAndTime() {
        Payout settled = pending().complete(PayoutStatus.SETTLED, COMPLETED_AT);

        assertThat(settled.status()).isEqualTo(PayoutStatus.SETTLED);
        assertThat(settled.completedAt()).isEqualTo(COMPLETED_AT);
        assertThat(settled.amount()).isEqualTo(new BigDecimal("48.50"));
    }

    @Test
    void endedPayoutCannotEndAgain() {
        Payout failed = pending().complete(PayoutStatus.FAILED, COMPLETED_AT);

        assertThatThrownBy(() -> failed.complete(PayoutStatus.SETTLED, COMPLETED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void payoutEndsSettledOrFailed() {
        assertThatThrownBy(() -> pending().complete(PayoutStatus.PENDING, COMPLETED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void completedAtIsSetExactlyWhenThePayoutHasEnded() {
        PayoutId id = new PayoutId("PO-0123456789");
        Currency eur = Currency.getInstance("EUR");
        BigDecimal amount = new BigDecimal("1.00");

        assertThatThrownBy(() -> new Payout(id, "MERCH-001", "EXT-1", eur, amount, REQUESTED_AT,
                                            PayoutStatus.PENDING, COMPLETED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Payout(id, "MERCH-001", "EXT-1", eur, amount, REQUESTED_AT,
                                            PayoutStatus.SETTLED, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void payoutIdHasThePoPrefix() {
        assertThatThrownBy(() -> new PayoutId("PAY-0123456789")).isInstanceOf(IllegalArgumentException.class);
    }
}

package org.example.payouts.core.ports.in;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestPayoutCommandTest {

    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Instant REQUESTED_AT = Instant.parse("2026-10-03T07:00:00Z");

    private static RequestPayoutCommand command(String amount) {
        return new RequestPayoutCommand("MERCH-001", "EXT-1", EUR, new BigDecimal(amount), REQUESTED_AT);
    }

    @Test
    void amountIsKeptAtTheCurrencyScale() {
        assertThat(command("48.5").amount()).isEqualTo(new BigDecimal("48.50"));
    }

    @Test
    void requestedAtIsTruncatedToMicroseconds() {
        RequestPayoutCommand command = new RequestPayoutCommand(
                "MERCH-001", "EXT-1", EUR, BigDecimal.ONE, Instant.parse("2026-10-03T07:00:00.123456789Z"));

        assertThat(command.requestedAt()).isEqualTo(Instant.parse("2026-10-03T07:00:00.123456Z"));
    }

    @Test
    void rejectsAmountsThatCannotBePaidOut() {
        assertThatThrownBy(() -> command("0")).isInstanceOf(InvalidPayoutException.class);
        assertThatThrownBy(() -> command("-1.00")).isInstanceOf(InvalidPayoutException.class);
        assertThatThrownBy(() -> command("1.001")).isInstanceOf(InvalidPayoutException.class);
        assertThatThrownBy(() -> command("1000000000000000")).isInstanceOf(InvalidPayoutException.class);
    }

    @Test
    void rejectsBlankIds() {
        assertThatThrownBy(() -> new RequestPayoutCommand(" ", "EXT-1", EUR, BigDecimal.ONE, REQUESTED_AT))
                .isInstanceOf(InvalidPayoutException.class);
        assertThatThrownBy(() -> new RequestPayoutCommand("MERCH-001", "", EUR, BigDecimal.ONE, REQUESTED_AT))
                .isInstanceOf(InvalidPayoutException.class);
    }
}

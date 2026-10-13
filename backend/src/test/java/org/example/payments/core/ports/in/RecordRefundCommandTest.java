package org.example.payments.core.ports.in;

import org.example.payments.core.domain.PaymentId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordRefundCommandTest {

    private static final PaymentId PAYMENT_ID = new PaymentId("PAY-0000000001");

    /** A null refundAmount is a full refund. */
    private static RecordRefundCommand command(String externalId, String refundedAt, String refundAmount) {
        return new RecordRefundCommand(PAYMENT_ID, externalId, Instant.parse(refundedAt),
                                       refundAmount == null ? null : new BigDecimal(refundAmount));
    }

    @Test
    void acceptsPartialAndFullRefunds() {
        assertThat(command("RE-1", "2026-10-02T08:15:00Z", "25.00").refundAmount()).isEqualByComparingTo("25.00");
        assertThat(command("RE-1", "2026-10-02T08:15:00Z", null).refundAmount()).isNull();
    }

    @Test
    void truncatesRefundedAtToMicroseconds() {
        assertThat(command("RE-1", "2026-10-02T08:15:00.123456789Z", "25.00").refundedAt())
                .isEqualTo(Instant.parse("2026-10-02T08:15:00.123456Z"));
    }

    @ParameterizedTest(name = "refundAmount {0} → {1}")
    @CsvSource({
            "0,                refundAmount must be positive",
            "0.00,             refundAmount must be positive",
            "-0.01,            refundAmount must be positive",
            "1000000000000000, refundAmount is too large"
    })
    void rejectsInvalidAmounts(String refundAmount, String expectedMessage) {
        assertThatThrownBy(() -> command("RE-1", "2026-10-02T08:15:00Z", refundAmount))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessageContaining(expectedMessage);
    }

    @Test
    void acceptsTheLargestAmount() {
        assertThat(command("RE-1", "2026-10-02T08:15:00Z", "999999999999999.99").refundAmount())
                .isEqualByComparingTo("999999999999999.99");
    }

    @ParameterizedTest(name = "rejects externalId \"{0}\"")
    @ValueSource(strings = {"", "  "})
    void rejectsBlankExternalId(String externalId) {
        assertThatThrownBy(() -> command(externalId, "2026-10-02T08:15:00Z", "25.00"))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessageContaining("externalId must not be blank");
    }

    @Test
    void limitsExternalIdLength() {
        String longest = "E".repeat(255);
        assertThat(command(longest, "2026-10-02T08:15:00Z", "25.00").externalId()).isEqualTo(longest);
        assertThatThrownBy(() -> command(longest + "E", "2026-10-02T08:15:00Z", "25.00"))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessageContaining("externalId must be at most 255 characters");
    }

    @ParameterizedTest(name = "accepts refundedAt {0}")
    @CsvSource({"0001-01-01T00:00:00Z", "9999-12-31T23:59:59.999999Z"})
    void acceptsRefundedAtAtTheYearBounds(String refundedAt) {
        assertThat(command("RE-1", refundedAt, "25.00").refundedAt()).isEqualTo(Instant.parse(refundedAt));
    }

    @ParameterizedTest(name = "rejects refundedAt {0}")
    @CsvSource({"0000-12-31T23:59:59.999999Z", "+10000-01-01T00:00:00Z", "+300000-01-01T00:00:00Z"})
    void rejectsRefundedAtOutsideTheYearBounds(String refundedAt) {
        assertThatThrownBy(() -> command("RE-1", refundedAt, "25.00"))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessageContaining("refundedAt must be between years 0001 and 9999");
    }
}

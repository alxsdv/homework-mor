package org.example.ledger.core.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostingTest {

    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Instant AT = Instant.parse("2026-09-30T21:30:00Z");

    private static PostingLine line(AccountKey account, String amount) {
        return new PostingLine(account, new BigDecimal(amount));
    }

    private static Posting posting(List<PostingLine> lines) {
        return new Posting("PAYMENT_SETTLED", "payment", "PAY-0000000001", AT, lines);
    }

    @Test
    void acceptsBalancedLines() {
        Posting posting = posting(List.of(
                line(AccountKey.cashClearing(EUR), "119.00"),
                line(AccountKey.taxPayable(EUR, "DE"), "-19.00"),
                line(AccountKey.feeRevenue(EUR), "-3.00"),
                line(AccountKey.merchantPayable(EUR, "MERCH-001"), "-97.00")));

        assertThat(posting.lines()).hasSize(4);
        assertThat(Posting.sum(posting.lines())).isEqualByComparingTo("0");
    }

    @Test
    void acceptsZeroAmountLines() {
        Posting posting = posting(List.of(
                line(AccountKey.cashClearing(EUR), "100.00"),
                line(AccountKey.taxPayable(EUR, "US"), "0.00"),
                line(AccountKey.merchantPayable(EUR, "MERCH-001"), "-100.00")));

        assertThat(posting.lines()).hasSize(3);
    }

    @Test
    void rejectsUnbalancedLines() {
        assertThatThrownBy(() -> posting(List.of(
                line(AccountKey.cashClearing(EUR), "119.00"),
                line(AccountKey.merchantPayable(EUR, "MERCH-001"), "-118.99"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sum to zero");
    }

    @Test
    void rejectsMixedCurrenciesEvenWhenTheNumbersCancelOut() {
        assertThatThrownBy(() -> posting(List.of(
                line(AccountKey.cashClearing(EUR), "100.00"),
                line(AccountKey.merchantPayable(USD, "MERCH-001"), "-100.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one currency");
    }

    @Test
    void rejectsFewerThanTwoLines() {
        assertThatThrownBy(() -> posting(List.of(line(AccountKey.cashClearing(EUR), "0.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least two lines");
    }

    @Test
    void copiesLinesDefensively() {
        List<PostingLine> lines = new ArrayList<>(List.of(
                line(AccountKey.cashClearing(EUR), "1.00"),
                line(AccountKey.merchantPayable(EUR, "MERCH-001"), "-1.00")));
        Posting posting = posting(lines);

        lines.clear();

        assertThat(posting.lines()).hasSize(2);
    }
}

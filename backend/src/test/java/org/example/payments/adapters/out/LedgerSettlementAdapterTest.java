package org.example.payments.adapters.out;

import org.example.ledger.core.domain.AccountKey;
import org.example.ledger.core.domain.LedgerEntry;
import org.example.ledger.core.domain.LedgerTransaction;
import org.example.payments.core.domain.Contact;
import org.example.payments.core.domain.Customer;
import org.example.payments.core.domain.Payment;
import org.example.payments.core.domain.PaymentId;
import org.example.payments.core.domain.Refund;
import org.example.payments.core.domain.RefundId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerSettlementAdapterTest {

    private static final Currency EUR = Currency.getInstance("EUR");

    private static Payment payment(String taxCountry, String total, String taxRate, String tax, String net,
                                   String fee, String merchant) {
        return new Payment(new PaymentId("PAY-0000000001"), "EXT-1", Instant.parse("2026-09-30T21:30:00Z"),
                           "MERCH-001",
                           new Customer("Anna", "Schmidt", "anna@example.com",
                                        new Contact("Hauptstrasse 12", null, taxCountry, "10115")),
                           taxCountry, EUR, new BigDecimal(total), new BigDecimal(taxRate), new BigDecimal(tax),
                           new BigDecimal(net), new BigDecimal("0.03"), new BigDecimal(fee),
                           new BigDecimal(merchant));
    }

    @Test
    void mapsSettledPaymentToFourBalancedLines() {
        Payment payment = payment("DE", "119.00", "0.19", "19.00", "100.00", "3.00", "97.00");

        LedgerTransaction transaction = LedgerSettlementAdapter.toLedgerTransaction(payment);

        assertThat(transaction.type()).isEqualTo("PAYMENT_SETTLED");
        assertThat(transaction.sourceType()).isEqualTo("payment");
        assertThat(transaction.sourceId()).isEqualTo("PAY-0000000001");
        assertThat(transaction.effectiveAt()).isEqualTo(payment.settledAt());
        assertThat(transaction.entries()).containsExactly(
                new LedgerEntry(AccountKey.cashClearing(EUR), new BigDecimal("119.00")),
                new LedgerEntry(AccountKey.taxPayable(EUR, "DE"), new BigDecimal("-19.00")),
                new LedgerEntry(AccountKey.feeRevenue(EUR), new BigDecimal("-3.00")),
                new LedgerEntry(AccountKey.merchantPayable(EUR, "MERCH-001"), new BigDecimal("-97.00")));
    }

    @Test
    void zeroTaxStillPostsATaxLine() {
        LedgerTransaction transaction = LedgerSettlementAdapter.toLedgerTransaction(
                payment("US", "100.00", "0", "0.00", "100.00", "3.00", "97.00"));

        assertThat(transaction.entries()).hasSize(4)
                .contains(new LedgerEntry(AccountKey.taxPayable(EUR, "US"), new BigDecimal("-0.00")));
    }

    private static Refund refund(String taxCountry, String total, String tax, String net, String fee,
                                 String merchant) {
        return new Refund(new RefundId("REF-0000000001"), new PaymentId("PAY-0000000001"), "MERCH-001", "RE-1",
                          Instant.parse("2026-10-02T08:15:00Z"), false, taxCountry, EUR, new BigDecimal("0.19"),
                          new BigDecimal("0.03"), new BigDecimal(total), new BigDecimal(tax), new BigDecimal(net),
                          new BigDecimal(fee), new BigDecimal(merchant));
    }

    @Test
    void mapsRefundToTheSettlementsLinesReversed() {
        Refund refund = refund("DE", "59.50", "9.50", "50.00", "1.50", "48.50");

        LedgerTransaction transaction = LedgerSettlementAdapter.toLedgerTransaction(refund);

        assertThat(transaction.type()).isEqualTo("PAYMENT_REFUNDED");
        assertThat(transaction.sourceType()).isEqualTo("refund");
        assertThat(transaction.sourceId()).isEqualTo("REF-0000000001");
        assertThat(transaction.effectiveAt()).isEqualTo(refund.refundedAt());
        assertThat(transaction.entries()).containsExactly(
                new LedgerEntry(AccountKey.cashClearing(EUR), new BigDecimal("-59.50")),
                new LedgerEntry(AccountKey.taxPayable(EUR, "DE"), new BigDecimal("9.50")),
                new LedgerEntry(AccountKey.feeRevenue(EUR), new BigDecimal("1.50")),
                new LedgerEntry(AccountKey.merchantPayable(EUR, "MERCH-001"), new BigDecimal("48.50")));
    }

    @Test
    void zeroTaxRefundStillPostsATaxLine() {
        LedgerTransaction transaction = LedgerSettlementAdapter.toLedgerTransaction(
                refund("US", "40.00", "0.00", "40.00", "1.20", "38.80"));

        assertThat(transaction.entries()).hasSize(4)
                .contains(new LedgerEntry(AccountKey.taxPayable(EUR, "US"), new BigDecimal("0.00")));
    }
}

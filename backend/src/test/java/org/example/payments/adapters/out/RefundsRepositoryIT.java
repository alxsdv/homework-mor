package org.example.payments.adapters.out;

import org.example.IntegrationTestBase;
import org.example.payments.core.domain.Contact;
import org.example.payments.core.domain.Customer;
import org.example.payments.core.domain.Payment;
import org.example.payments.core.domain.PaymentId;
import org.example.payments.core.domain.Refund;
import org.example.payments.core.domain.RefundAmounts;
import org.example.payments.core.domain.RefundId;
import org.example.payments.core.ports.out.DuplicateRefundExternalIdException;
import org.example.payments.core.ports.out.DuplicateRefundIdException;
import org.example.payments.core.ports.out.PaymentsRepository;
import org.example.payments.core.ports.out.RefundsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.example.jooq.Tables.REFUNDS;

class RefundsRepositoryIT extends IntegrationTestBase {

    private static final Currency EUR = Currency.getInstance("EUR");

    @Autowired
    private PaymentsRepository paymentsRepository;

    @Autowired
    private RefundsRepository refundsRepository;

    private static Payment payment(String paymentId, String externalId) {
        return new Payment(new PaymentId(paymentId), externalId, Instant.parse("2026-09-30T21:30:00Z"), "MERCH-001",
                           new Customer("Anna", "Schmidt", "anna.schmidt@example.com",
                                        new Contact("Hauptstrasse 12", null, "DE", "10115")),
                           "DE", EUR, new BigDecimal("119.00"), new BigDecimal("0.19"), new BigDecimal("19.00"),
                           new BigDecimal("100.00"), new BigDecimal("0.03"), new BigDecimal("3.00"),
                           new BigDecimal("97.00"));
    }

    private static Refund refund(String refundId, String paymentId, String externalId, String total, String tax,
                                 String net, String fee, String merchant) {
        return new Refund(new RefundId(refundId), new PaymentId(paymentId), "MERCH-001", externalId,
                          Instant.parse("2026-10-02T08:15:00.123456Z"), false, "DE", EUR, new BigDecimal("0.19"),
                          new BigDecimal("0.03"), new BigDecimal(total), new BigDecimal(tax), new BigDecimal(net),
                          new BigDecimal(fee), new BigDecimal(merchant));
    }

    private static Refund halfOfPaymentOne() {
        return refund("REF-0000000001", "PAY-0000000001", "RE-1", "59.50", "9.50", "50.00", "1.50", "48.50");
    }

    /** Runs after IntegrationTestBase has emptied the tables. */
    @BeforeEach
    void storePayments() {
        paymentsRepository.addPayment(payment("PAY-0000000001", "EXT-1"));
        paymentsRepository.addPayment(payment("PAY-0000000002", "EXT-2"));
    }

    @Test
    void storesAndFindsRefund() {
        Refund refund = halfOfPaymentOne();

        refundsRepository.addRefund(refund);

        assertThat(dsl.fetchCount(REFUNDS)).isEqualTo(1);
        Refund found = refundsRepository.findByMerchantAndExternalId("MERCH-001", "RE-1").orElseThrow();
        assertThat(found)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(refund);
        // numeric(19,4) would come back as 59.5000 without normalization; microseconds survive.
        assertThat(found.totalAmount()).isEqualTo(new BigDecimal("59.50"));
        assertThat(found.refundedAt()).isEqualTo(Instant.parse("2026-10-02T08:15:00.123456Z"));
    }

    @Test
    void keepsTheFullRefundFlag() {
        Refund full = new Refund(new RefundId("REF-0000000001"), new PaymentId("PAY-0000000001"), "MERCH-001",
                                 "RE-1", Instant.parse("2026-10-02T08:15:00Z"), true, "DE", EUR,
                                 new BigDecimal("0.19"), new BigDecimal("0.03"), new BigDecimal("119.00"),
                                 new BigDecimal("19.00"), new BigDecimal("100.00"), new BigDecimal("3.00"),
                                 new BigDecimal("97.00"));
        refundsRepository.addRefund(full);

        assertThat(refundsRepository.findByMerchantAndExternalId("MERCH-001", "RE-1").orElseThrow().fullRefund())
                .isTrue();
    }

    @Test
    void refundedSoFarIsZeroWithoutRefunds() {
        assertThat(refundsRepository.refundedSoFar(new PaymentId("PAY-0000000001"), EUR))
                .isEqualTo(RefundAmounts.none(EUR));
    }

    @Test
    void refundedSoFarSumsOnlyThatPaymentsRefunds() {
        refundsRepository.addRefund(halfOfPaymentOne());
        refundsRepository.addRefund(
                refund("REF-0000000002", "PAY-0000000001", "RE-2", "30.00", "4.79", "25.21", "0.76", "24.45"));
        refundsRepository.addRefund(
                refund("REF-0000000003", "PAY-0000000002", "RE-3", "10.00", "1.60", "8.40", "0.25", "8.15"));

        // isEqualTo also pins the currency's scale.
        assertThat(refundsRepository.refundedSoFar(new PaymentId("PAY-0000000001"), EUR))
                .isEqualTo(new RefundAmounts(new BigDecimal("89.50"), new BigDecimal("14.29"),
                                             new BigDecimal("75.21"), new BigDecimal("2.26"),
                                             new BigDecimal("72.95")));
    }

    @Test
    void matchesIdsExactly() {
        refundsRepository.addRefund(halfOfPaymentOne());

        assertThat(refundsRepository.findByMerchantAndExternalId("MERCH-001", "re-1")).isEmpty();
        assertThat(refundsRepository.findByMerchantAndExternalId("MERCH-002", "RE-1")).isEmpty();
    }

    @Test
    void reportsDuplicatePublicId() {
        refundsRepository.addRefund(halfOfPaymentOne());

        assertThatThrownBy(() -> refundsRepository.addRefund(
                refund("REF-0000000001", "PAY-0000000002", "RE-2", "10.00", "1.60", "8.40", "0.25", "8.15")))
                .isInstanceOfSatisfying(DuplicateRefundIdException.class,
                                        e -> assertThat(e.refundId().value()).isEqualTo("REF-0000000001"));
    }

    @Test
    void reportsDuplicateMerchantAndExternalId() {
        refundsRepository.addRefund(halfOfPaymentOne());

        assertThatThrownBy(() -> refundsRepository.addRefund(
                refund("REF-0000000002", "PAY-0000000002", "RE-1", "10.00", "1.60", "8.40", "0.25", "8.15")))
                .isInstanceOfSatisfying(DuplicateRefundExternalIdException.class, e -> {
                    assertThat(e.merchantId()).isEqualTo("MERCH-001");
                    assertThat(e.externalId()).isEqualTo("RE-1");
                });
    }
}

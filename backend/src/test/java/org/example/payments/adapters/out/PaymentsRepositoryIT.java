package org.example.payments.adapters.out;

import org.example.IntegrationTestBase;
import org.example.payments.core.domain.Contact;
import org.example.payments.core.domain.Customer;
import org.example.payments.core.domain.Payment;
import org.example.payments.core.domain.PaymentId;
import org.example.payments.core.ports.out.DuplicateExternalIdException;
import org.example.payments.core.ports.out.DuplicatePaymentIdException;
import org.example.payments.core.ports.out.PaymentsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.example.jooq.Tables.PAYMENTS;

class PaymentsRepositoryIT extends IntegrationTestBase {

    @Autowired
    private PaymentsRepository paymentsRepository;

    private static Payment eurPayment(String paymentId, String externalId, String addressLine2) {
        return new Payment(new PaymentId(paymentId), externalId, Instant.parse("2026-09-30T21:30:00.123456Z"),
                           "MERCH-001",
                           new Customer("Anna", "Schmidt", "anna.schmidt@example.com",
                                        new Contact("Hauptstrasse 12", addressLine2, "DE", "10115")),
                           "DE", Currency.getInstance("EUR"), new BigDecimal("119.00"), new BigDecimal("0.19"),
                           new BigDecimal("19.00"), new BigDecimal("100.00"), new BigDecimal("0.03"),
                           new BigDecimal("3.00"), new BigDecimal("97.00"));
    }

    private static Payment jpyPayment() {
        return new Payment(new PaymentId("PAY-0000000009"), "EXT-JPY", Instant.parse("2026-09-30T21:30:00Z"),
                           "MERCH-001",
                           new Customer("Yuki", "Tanaka", "yuki@example.com",
                                        new Contact("1-1 Chiyoda", null, "JP", "100-0001")),
                           "JP", Currency.getInstance("JPY"), new BigDecimal("1000"), new BigDecimal("0.10"),
                           new BigDecimal("91"), new BigDecimal("909"), new BigDecimal("0.03"),
                           new BigDecimal("27"), new BigDecimal("882"));
    }

    @Test
    void storesAndFindsPayment() {
        Payment payment = eurPayment("PAY-0000000001", "EXT-1", "Wohnung 4");

        paymentsRepository.addPayment(payment);

        assertThat(dsl.fetchCount(PAYMENTS)).isEqualTo(1);
        assertThat(paymentsRepository.findByMerchantAndExternalId("MERCH-001", "EXT-1"))
                .hasValueSatisfying(found -> assertThat(found)
                        .usingRecursiveComparison()
                        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                        .isEqualTo(payment));
    }

    @Test
    void keepsMicrosecondsAndNullAddressLine() {
        paymentsRepository.addPayment(eurPayment("PAY-0000000001", "EXT-1", null));

        Payment found = paymentsRepository.findByMerchantAndExternalId("MERCH-001", "EXT-1").orElseThrow();

        assertThat(found.settledAt()).isEqualTo(Instant.parse("2026-09-30T21:30:00.123456Z"));
        assertThat(found.customer().billingContact().addressLine2()).isNull();
    }

    @Test
    void readsAmountsBackWithTheCurrencyScale() {
        paymentsRepository.addPayment(eurPayment("PAY-0000000001", "EXT-1", null));
        paymentsRepository.addPayment(jpyPayment());

        // numeric(19,4) would come back as 119.0000 / 1000.0000 without normalization.
        assertThat(paymentsRepository.findByMerchantAndExternalId("MERCH-001", "EXT-1").orElseThrow().totalAmount())
                .isEqualTo(new BigDecimal("119.00"));
        assertThat(paymentsRepository.findByMerchantAndExternalId("MERCH-001", "EXT-JPY").orElseThrow().merchantAmount())
                .isEqualTo(new BigDecimal("882"));
    }

    @Test
    void matchesIdsExactly() {
        paymentsRepository.addPayment(eurPayment("PAY-0000000001", "EXT-1", null));

        assertThat(paymentsRepository.findByMerchantAndExternalId("MERCH-001", "ext-1")).isEmpty();
        assertThat(paymentsRepository.findByMerchantAndExternalId("merch-001", "EXT-1")).isEmpty();
        assertThat(paymentsRepository.findByMerchantAndExternalId("MERCH-002", "EXT-1")).isEmpty();
    }

    @Test
    void reportsDuplicatePublicId() {
        paymentsRepository.addPayment(eurPayment("PAY-0000000001", "EXT-1", null));

        assertThatThrownBy(() -> paymentsRepository.addPayment(eurPayment("PAY-0000000001", "EXT-2", null)))
                .isInstanceOfSatisfying(DuplicatePaymentIdException.class,
                                        e -> assertThat(e.paymentId().value()).isEqualTo("PAY-0000000001"));
    }

    @Test
    void reportsDuplicateMerchantAndExternalId() {
        paymentsRepository.addPayment(eurPayment("PAY-0000000001", "EXT-1", null));

        assertThatThrownBy(() -> paymentsRepository.addPayment(eurPayment("PAY-0000000002", "EXT-1", null)))
                .isInstanceOfSatisfying(DuplicateExternalIdException.class, e -> {
                    assertThat(e.merchantId()).isEqualTo("MERCH-001");
                    assertThat(e.externalId()).isEqualTo("EXT-1");
                });
    }
}

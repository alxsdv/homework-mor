package org.example.payments.adapters.out;

import org.example.payments.core.domain.Contact;
import org.example.payments.core.domain.Customer;
import org.example.payments.core.domain.Payment;
import org.example.payments.core.domain.PaymentId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Map;
import java.util.stream.Stream;

public class PaymentsFixture {
    static void fill(Map<PaymentId, Payment> payments) {
        Stream.of(
                payment(
                        new PaymentId("PAY-0000000001"),
                        "EXT-0001",
                        LocalDate.of(2026, 1, 15),
                        "MERCH-001",
                        new Customer(
                                "Anna",
                                "Schmidt",
                                "anna.schmidt@example.com",
                                new Contact(
                                        "Hauptstrasse 12",
                                        "Wohnung 4",
                                        "DE",
                                        "10115")),
                        "EUR",
                        "100.00",
                        "0.20"),
                payment(
                        new PaymentId("PAY-0000000002"),
                        "EXT-0002",
                        LocalDate.of(2026, 2, 3),
                        "MERCH-002",
                        new Customer(
                                "Louis",
                                "Martin",
                                "louis.martin@example.com",
                                new Contact(
                                        "8 Rue de Rivoli",
                                        null,
                                        "FR",
                                        "75001")),
                        "EUR",
                        "250.00",
                        "0.10"),
                payment(
                        new PaymentId("PAY-0000000003"),
                        "EXT-0003",
                        LocalDate.of(2026, 3, 22), "MERCH-001",
                        new Customer(
                                "John",
                                "Smith",
                                "john.smith@example.com",
                                new Contact(
                                        "500 Market Street",
                                        "Suite 200",
                                        "US",
                                        "94105")),
                        "USD",
                        "49.99",
                        "0.00"),
                payment(
                        new PaymentId("PAY-0000000004"),
                        "EXT-0004",
                        LocalDate.of(2026, 4, 9),
                        "MERCH-003",
                        new Customer(
                                "Emily",
                                "Clarke",
                                "emily.clarke@example.com",
                                new Contact(
                                        "221B Baker Street",
                                        null,
                                        "GB",
                                        "NW1 6XE")),
                        "GBP",
                        "80.00",
                        "0.20"),
                payment(
                        new PaymentId("PAY-0000000005"),
                        "EXT-0005",
                        LocalDate.of(2026, 5, 30),
                        "MERCH-002",
                        new Customer(
                                "Daan",
                                "de Vries",
                                "daan.devries@example.com",
                                new Contact(
                                        "Damrak 70",
                                        null,
                                        "NL",
                                        "1012 LM")),
                        "EUR",
                        "1500.00",
                        "0.21")
                 ).forEach(p -> payments.put(p.paymentId(), p));
    }

    private static Payment payment(PaymentId paymentId, String externalId, LocalDate paymentDate, String merchantId,
                                   Customer customer, String currency, String netAmount, String taxRate) {
        BigDecimal net = new BigDecimal(netAmount);
        BigDecimal rate = new BigDecimal(taxRate);
        BigDecimal tax = net.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        return new Payment(
                paymentId, externalId, paymentDate, merchantId, customer, Currency.getInstance(currency),
                net.add(tax), rate, tax,
                net);
    }
}

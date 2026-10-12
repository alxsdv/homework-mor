package org.example.payments.adapters.in;

import org.example.payments.core.domain.Contact;
import org.example.payments.core.domain.Customer;
import org.example.payments.core.ports.in.RecordSettledPaymentCommand;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;

/** Demo payments for local exploration; fixed externalIds keep re-seeding idempotent. */
final class DemoPayments {

    private DemoPayments() {}

    static List<RecordSettledPaymentCommand> commands() {
        return List.of(
                command("EXT-0001", "2026-01-15T10:00:00Z", "MERCH-001",
                        customer("Anna", "Schmidt", "anna.schmidt@example.com",
                                 "Hauptstrasse 12", "Wohnung 4", "DE", "10115"),
                        "EUR", "120.00", "0.20"),
                command("EXT-0002", "2026-02-03T10:00:00Z", "MERCH-002",
                        customer("Louis", "Martin", "louis.martin@example.com",
                                 "8 Rue de Rivoli", null, "FR", "75001"),
                        "EUR", "275.00", "0.10"),
                command("EXT-0003", "2026-03-22T10:00:00Z", "MERCH-001",
                        customer("John", "Smith", "john.smith@example.com",
                                 "500 Market Street", "Suite 200", "US", "94105"),
                        "USD", "49.99", "0.00"),
                command("EXT-0004", "2026-04-09T10:00:00Z", "MERCH-003",
                        customer("Emily", "Clarke", "emily.clarke@example.com",
                                 "221B Baker Street", null, "GB", "NW1 6XE"),
                        "GBP", "96.00", "0.20"),
                command("EXT-0005", "2026-05-30T10:00:00Z", "MERCH-002",
                        customer("Daan", "de Vries", "daan.devries@example.com",
                                 "Damrak 70", null, "NL", "1012 LM"),
                        "EUR", "1815.00", "0.21"));
    }

    private static RecordSettledPaymentCommand command(String externalId, String settledAt, String merchantId,
                                                       Customer customer, String currency, String totalAmount,
                                                       String taxRate) {
        return new RecordSettledPaymentCommand(externalId, Instant.parse(settledAt), merchantId, customer,
                                               Currency.getInstance(currency), new BigDecimal(totalAmount),
                                               new BigDecimal(taxRate));
    }

    private static Customer customer(String firstName, String lastName, String email, String addressLine1,
                                     @Nullable String addressLine2, String countryCode, String postalCode) {
        return new Customer(firstName, lastName, email,
                            new Contact(addressLine1, addressLine2, countryCode, postalCode));
    }
}

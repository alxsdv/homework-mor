package org.example.payments.core.ports.in;

import org.example.payments.core.domain.Contact;
import org.example.payments.core.domain.Customer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordSettledPaymentCommandTest {

    private static RecordSettledPaymentCommand command(String externalId, String merchantId, String settledAt,
                                                       String currency, String totalAmount, String taxRate,
                                                       String countryCode) {
        Customer customer = new Customer("Anna", "Schmidt", "anna.schmidt@example.com",
                                         new Contact("Hauptstrasse 12", null, countryCode, "10115"));
        return new RecordSettledPaymentCommand(externalId, Instant.parse(settledAt), merchantId, customer,
                                               Currency.getInstance(currency), new BigDecimal(totalAmount),
                                               new BigDecimal(taxRate));
    }

    private static RecordSettledPaymentCommand command(String currency, String totalAmount, String taxRate,
                                                       String countryCode) {
        return command("EXT-1", "MERCH-001", "2026-09-30T21:30:00Z", currency, totalAmount, taxRate, countryCode);
    }

    @Test
    void acceptsValidCommand() {
        RecordSettledPaymentCommand command = command("EUR", "119.00", "0.19", "DE");

        assertThat(command.totalAmount()).isEqualByComparingTo("119.00");
    }

    @Test
    void truncatesSettledAtToMicroseconds() {
        RecordSettledPaymentCommand command =
                command("EXT-1", "MERCH-001", "2026-09-30T21:30:00.123456789Z", "EUR", "119.00", "0.19", "DE");

        assertThat(command.settledAt()).isEqualTo(Instant.parse("2026-09-30T21:30:00.123456Z"));
    }

    @Test
    void acceptsZeroDecimalCurrencyWithoutDecimals() {
        assertThat(command("JPY", "1000", "0.10", "JP").totalAmount()).isEqualByComparingTo("1000");
    }

    @ParameterizedTest(name = "{0} {1} at tax {2} in {3} → {4}")
    @CsvSource({
            "EUR, -0.01,            0.19,      DE, totalAmount cannot be negative",
            "EUR, 1000000000000000, 0.19,      DE, totalAmount is too large",
            "EUR, 10.001,           0.19,      DE, more decimal places than EUR allows",
            "JPY, 10.5,             0.10,      JP, more decimal places than JPY allows",
            "XAU, 10,               0.19,      DE, has no minor units",
            "EUR, 10.00,            -0.01,     DE, taxRate cannot be negative",
            "EUR, 10.00,            19,        DE, taxRate must be a fraction below 1",
            "EUR, 10.00,            1,         DE, taxRate must be a fraction below 1",
            "EUR, 10.00,            0.1234567, DE, taxRate can have at most 6 decimal places",
            "EUR, 10.00,            0.19,      de, ISO 3166-1 alpha-2",
            "EUR, 10.00,            0.19,      XX, ISO 3166-1 alpha-2"
    })
    void rejectsInvalidValues(String currency, String totalAmount, String taxRate, String countryCode,
                              String expectedMessage) {
        assertThatThrownBy(() -> command(currency, totalAmount, taxRate, countryCode))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessageContaining(expectedMessage);
    }

    @Test
    void rejectsBlankIds() {
        assertThatThrownBy(() -> command(" ", "MERCH-001", "2026-09-30T21:30:00Z", "EUR", "1.00", "0.19", "DE"))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessageContaining("externalId must not be blank");
        assertThatThrownBy(() -> command("EXT-1", "", "2026-09-30T21:30:00Z", "EUR", "1.00", "0.19", "DE"))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessageContaining("merchantId must not be blank");
    }

    @Test
    void limitsExternalIdLength() {
        String longest = "E".repeat(255);
        assertThat(command(longest, "MERCH-001", "2026-09-30T21:30:00Z", "EUR", "1.00", "0.19", "DE").externalId())
                .isEqualTo(longest);
        assertThatThrownBy(() -> command(longest + "E", "MERCH-001", "2026-09-30T21:30:00Z", "EUR", "1.00", "0.19", "DE"))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessageContaining("externalId must be at most 255 characters");
    }

    @ParameterizedTest(name = "accepts settledAt {0}")
    @CsvSource({"0001-01-01T00:00:00Z", "9999-12-31T23:59:59.999999Z"})
    void acceptsSettledAtAtTheYearBounds(String settledAt) {
        assertThat(command("EXT-1", "MERCH-001", settledAt, "EUR", "1.00", "0.19", "DE").settledAt())
                .isEqualTo(Instant.parse(settledAt));
    }

    @ParameterizedTest(name = "rejects settledAt {0}")
    @CsvSource({"0000-12-31T23:59:59.999999Z", "+10000-01-01T00:00:00Z", "+300000-01-01T00:00:00Z"})
    void rejectsSettledAtOutsideTheYearBounds(String settledAt) {
        assertThatThrownBy(() -> command("EXT-1", "MERCH-001", settledAt, "EUR", "1.00", "0.19", "DE"))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessageContaining("settledAt must be between years 0001 and 9999");
    }
}

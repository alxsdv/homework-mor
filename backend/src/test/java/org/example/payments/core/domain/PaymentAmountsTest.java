package org.example.payments.core.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentAmountsTest {

    @ParameterizedTest(name = "{0} {3}, tax {1}, fee {2}")
    @CsvSource({
            // total, taxRate, feeRate, currency, tax,   net,    fee,   merchant
            "119.00,  0.19,    0.03,    EUR,      19.00, 100.00, 3.00,  97.00",
            "124.00,  0.24,    0.03,    EUR,      24.00, 100.00, 3.00,  97.00",
            "108.00,  0.08,    0.03,    USD,      8.00,  100.00, 3.00,  97.00",
            "238.00,  0.19,    0.05,    EUR,      38.00, 200.00, 10.00, 190.00",
            "10.00,   0.19,    0.035,   EUR,      1.60,  8.40,   0.29,  8.11",   // net and fee both round
            "0.50,    0,       0.03,    EUR,      0.00,  0.50,   0.02,  0.48",   // fee 0.015 rounds HALF_UP
            "1000,    0.10,    0.03,    JPY,      91,    909,    27,    882",    // 0-decimal currency
            "0.00,    0.19,    0.03,    EUR,      0.00,  0.00,   0.00,  0.00"
    })
    void splitsTotalIntoTaxFeeAndMerchantShare(String total, String taxRate, String feeRate, String currency,
                                               String tax, String net, String fee, String merchant) {
        PaymentAmounts amounts = PaymentAmounts.calculate(
                new BigDecimal(total), new BigDecimal(taxRate), new BigDecimal(feeRate), Currency.getInstance(currency));

        // isEqualTo (not isEqualByComparingTo) also pins the scale to the currency's decimal places.
        assertThat(amounts.taxAmount()).isEqualTo(new BigDecimal(tax));
        assertThat(amounts.netAmount()).isEqualTo(new BigDecimal(net));
        assertThat(amounts.feeAmount()).isEqualTo(new BigDecimal(fee));
        assertThat(amounts.merchantAmount()).isEqualTo(new BigDecimal(merchant));
    }

    @Test
    void normalizesTotalToCurrencyScale() {
        PaymentAmounts amounts = PaymentAmounts.calculate(
                new BigDecimal("119"), new BigDecimal("0.19"), new BigDecimal("0.03"), Currency.getInstance("EUR"));

        assertThat(amounts.totalAmount()).isEqualTo(new BigDecimal("119.00"));
    }

    @Test
    void taxFeeAndMerchantShareAlwaysAddUpToTotal() {
        Currency eur = Currency.getInstance("EUR");
        for (int cents = 0; cents <= 20_000; cents += 7) {
            BigDecimal total = BigDecimal.valueOf(cents, 2);
            for (String taxRate : List.of("0", "0.07", "0.19", "0.21", "0.255")) {
                for (String feeRate : List.of("0", "0.025", "0.03", "0.0475")) {
                    PaymentAmounts a = PaymentAmounts.calculate(total, new BigDecimal(taxRate), new BigDecimal(feeRate), eur);

                    assertThat(a.taxAmount().add(a.feeAmount()).add(a.merchantAmount()))
                            .as("total %s, tax rate %s, fee rate %s", total, taxRate, feeRate)
                            .isEqualByComparingTo(total);
                    assertThat(a.merchantAmount().signum()).isGreaterThanOrEqualTo(0);
                }
            }
        }
    }

    @Test
    void rejectsTotalWithMoreDecimalsThanTheCurrencyAllows() {
        assertThatThrownBy(() -> PaymentAmounts.calculate(
                new BigDecimal("10.001"), BigDecimal.ZERO, BigDecimal.ZERO, Currency.getInstance("EUR")))
                .isInstanceOf(ArithmeticException.class);
    }
}

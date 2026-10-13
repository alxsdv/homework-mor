package org.example.payments.core.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefundAmountsTest {

    private static Payment payment(String total, String taxRate, String feeRate, String currencyCode) {
        Currency currency = Currency.getInstance(currencyCode);
        PaymentAmounts a = PaymentAmounts.calculate(new BigDecimal(total), new BigDecimal(taxRate),
                                                    new BigDecimal(feeRate), currency);
        return new Payment(new PaymentId("PAY-0000000001"), "EXT-1", Instant.parse("2026-09-30T21:30:00Z"),
                           "MERCH-001",
                           new Customer("Anna", "Schmidt", "anna@example.com",
                                        new Contact("Hauptstrasse 12", null, "DE", "10115")),
                           "DE", currency, a.totalAmount(), new BigDecimal(taxRate), a.taxAmount(), a.netAmount(),
                           new BigDecimal(feeRate), a.feeAmount(), a.merchantAmount());
    }

    private static RefundAmounts amounts(String total, String tax, String net, String fee, String merchant) {
        return new RefundAmounts(new BigDecimal(total), new BigDecimal(tax), new BigDecimal(net),
                                 new BigDecimal(fee), new BigDecimal(merchant));
    }

    private static RefundAmounts plus(RefundAmounts a, RefundAmounts b) {
        return new RefundAmounts(a.totalAmount().add(b.totalAmount()), a.taxAmount().add(b.taxAmount()),
                                 a.netAmount().add(b.netAmount()), a.feeAmount().add(b.feeAmount()),
                                 a.merchantAmount().add(b.merchantAmount()));
    }

    /** Records each refund in turn; a null amount refunds whatever is left. */
    private static List<RefundAmounts> refundInTurn(Payment payment, String... amounts) {
        List<RefundAmounts> refunds = new ArrayList<>();
        RefundAmounts refunded = RefundAmounts.none(payment.currency());
        for (String amount : amounts) {
            BigDecimal value = amount == null
                    ? payment.totalAmount().subtract(refunded.totalAmount())
                    : new BigDecimal(amount);
            RefundAmounts refund = RefundAmounts.calculate(payment, refunded, value);
            refunds.add(refund);
            refunded = plus(refunded, refund);
        }
        return refunds;
    }

    /** Every refund is non-negative and adds up to its total; together they are exactly the payment's split. */
    private static void assertAddsUpTo(Payment payment, List<RefundAmounts> refunds) {
        RefundAmounts sum = RefundAmounts.none(payment.currency());
        for (RefundAmounts refund : refunds) {
            assertThat(List.of(refund.taxAmount(), refund.netAmount(), refund.feeAmount(), refund.merchantAmount()))
                    .as("%s of payment %s", refund, payment.totalAmount())
                    .allSatisfy(part -> assertThat(part.signum()).isGreaterThanOrEqualTo(0));
            assertThat(refund.taxAmount().add(refund.feeAmount()).add(refund.merchantAmount()))
                    .isEqualByComparingTo(refund.totalAmount());
            sum = plus(sum, refund);
        }
        // isEqualTo on the record also pins every amount to the currency's scale.
        assertThat(sum).isEqualTo(new RefundAmounts(payment.totalAmount(), payment.taxAmount(),
                                                    payment.netAmount(), payment.feeAmount(),
                                                    payment.merchantAmount()));
    }

    @Test
    void splitsPartialRefundsAndTheRemainderInProportion() {
        // 100.00 EUR at 19% tax and a 3% fee: tax 15.97, net 84.03, fee 2.52, merchant 81.51.
        Payment payment = payment("100.00", "0.19", "0.03", "EUR");

        List<RefundAmounts> refunds = refundInTurn(payment, "25.00", "30.00", null);

        assertThat(refunds).containsExactly(
                amounts("25.00", "3.99", "21.01", "0.63", "20.38"),
                amounts("30.00", "4.79", "25.21", "0.76", "24.45"),
                amounts("45.00", "7.19", "37.81", "1.13", "36.68"));
        assertAddsUpTo(payment, refunds);
    }

    @Test
    void singleFullRefundIsThePaymentsOwnSplit() {
        List<RefundAmounts> refunds = refundInTurn(payment("119.00", "0.19", "0.03", "EUR"), (String) null);

        assertThat(refunds).containsExactly(amounts("119.00", "19.00", "100.00", "3.00", "97.00"));
    }

    @Test
    void zeroTaxPaymentRefundsNoTax() {
        assertThat(refundInTurn(payment("100.00", "0", "0.03", "EUR"), "40.00"))
                .containsExactly(amounts("40.00", "0.00", "40.00", "1.20", "38.80"));
    }

    @Test
    void zeroDecimalCurrency() {
        // 1000 JPY at 10% tax and a 3% fee: tax 91, net 909, fee 27, merchant 882.
        Payment payment = payment("1000", "0.10", "0.03", "JPY");

        List<RefundAmounts> refunds = refundInTurn(payment, "333", "333", null);

        assertThat(refunds).containsExactly(
                amounts("333", "30", "303", "9", "294"),
                amounts("333", "31", "302", "9", "293"),
                amounts("334", "30", "304", "9", "295"));
        assertAddsUpTo(payment, refunds);
    }

    @Test
    void centByCentRefundsNeverGoNegative() {
        Payment payment = payment("1.19", "0.19", "0.03", "EUR");
        String[] cents = new String[119];
        Arrays.fill(cents, "0.01");

        assertAddsUpTo(payment, refundInTurn(payment, cents));
    }

    @Test
    void randomRefundSequencesAddUpToThePaymentExactly() {
        Random random = new Random(20261013);
        List<String> taxRates = List.of("0", "0.07", "0.19", "0.255", "0.99");
        List<String> feeRates = List.of("0", "0.03", "0.5", "0.999");
        for (int i = 0; i < 5_000; i++) {
            BigDecimal total = BigDecimal.valueOf(random.nextInt(1, 100_001), 2);
            Payment payment = payment(total.toPlainString(), taxRates.get(random.nextInt(taxRates.size())),
                                      feeRates.get(random.nextInt(feeRates.size())), "EUR");
            List<String> amounts = new ArrayList<>();
            long centsLeft = total.unscaledValue().longValueExact();
            while (centsLeft > 0) {
                long cents = random.nextLong(1, centsLeft + 1);
                amounts.add(BigDecimal.valueOf(cents, 2).toPlainString());
                centsLeft -= cents;
            }

            assertAddsUpTo(payment, refundInTurn(payment, amounts.toArray(String[]::new)));
        }
    }

    @Test
    void rejectsAmountsOutsideWhatIsLeft() {
        Payment payment = payment("119.00", "0.19", "0.03", "EUR");
        RefundAmounts none = RefundAmounts.none(payment.currency());
        RefundAmounts all = amounts("119.00", "19.00", "100.00", "3.00", "97.00");

        assertThatThrownBy(() -> RefundAmounts.calculate(payment, none, new BigDecimal("119.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefundAmounts.calculate(payment, none, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefundAmounts.calculate(payment, all, new BigDecimal("0.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noneIsZeroAtTheCurrencyScale() {
        assertThat(RefundAmounts.none(Currency.getInstance("EUR"))).isEqualTo(amounts("0.00", "0.00", "0.00", "0.00", "0.00"));
        assertThat(RefundAmounts.none(Currency.getInstance("JPY"))).isEqualTo(amounts("0", "0", "0", "0", "0"));
    }
}

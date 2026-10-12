package org.example.payments.core.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * How a settled payment's total splits into tax, our fee and the merchant's share.
 * Every amount has the currency's number of decimal places; rounding remainders go to the merchant,
 * so {@code taxAmount + feeAmount + merchantAmount == totalAmount} always holds.
 */
public record PaymentAmounts(
        BigDecimal totalAmount,
        BigDecimal taxAmount,
        BigDecimal netAmount,
        BigDecimal feeAmount,
        BigDecimal merchantAmount) {

    public static PaymentAmounts calculate(BigDecimal totalAmount, BigDecimal taxRate, BigDecimal feeRate,
                                           Currency currency) {
        int digits = currency.getDefaultFractionDigits();
        // UNNECESSARY: RecordSettledPaymentCommand already rejects totals with more decimals than the currency allows.
        BigDecimal total = totalAmount.setScale(digits, RoundingMode.UNNECESSARY);
        BigDecimal net = total.divide(BigDecimal.ONE.add(taxRate), digits, RoundingMode.HALF_UP);
        BigDecimal tax = total.subtract(net);
        BigDecimal fee = net.multiply(feeRate).setScale(digits, RoundingMode.HALF_UP);
        BigDecimal merchant = net.subtract(fee);
        return new PaymentAmounts(total, tax, net, fee, merchant);
    }
}

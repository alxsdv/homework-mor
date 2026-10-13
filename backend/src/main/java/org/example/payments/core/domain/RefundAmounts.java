package org.example.payments.core.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * How a refund splits into tax, our fee and the merchant's share, or the sum of several refunds.
 * Splits are cumulative: a refund is the payment's proportional split of everything refunded up to and including
 * it, minus the same for everything refunded before it. Rounding therefore never accumulates, the refunds of a
 * fully refunded payment add up to exactly the payment's own split, and no part is ever negative.
 */
public record RefundAmounts(
        BigDecimal totalAmount,
        BigDecimal taxAmount,
        BigDecimal netAmount,
        BigDecimal feeAmount,
        BigDecimal merchantAmount) {

    /** Nothing refunded yet, at the currency's scale. */
    public static RefundAmounts none(Currency currency) {
        BigDecimal zero = BigDecimal.ZERO.setScale(currency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY);
        return new RefundAmounts(zero, zero, zero, zero, zero);
    }

    /**
     * The split of a new refund of refundAmount, given what was refunded before it. Throws IllegalArgumentException
     * unless refundAmount is positive and within what's left: callers reject such requests before getting here.
     */
    public static RefundAmounts calculate(Payment payment, RefundAmounts alreadyRefunded, BigDecimal refundAmount) {
        int digits = payment.currency().getDefaultFractionDigits();
        // UNNECESSARY: amounts with more decimals than the currency allows are rejected before getting here.
        BigDecimal amount = refundAmount.setScale(digits, RoundingMode.UNNECESSARY);
        BigDecimal refundedTotal = alreadyRefunded.totalAmount().add(amount);
        if (amount.signum() <= 0 || refundedTotal.compareTo(payment.totalAmount()) > 0) {
            throw new IllegalArgumentException(
                    "Refund of " + amount + " is not within the "
                    + payment.totalAmount().subtract(alreadyRefunded.totalAmount()) + " left on payment "
                    + payment.paymentId().value());
        }
        return cumulative(payment, refundedTotal, digits).minus(alreadyRefunded);
    }

    /**
     * The payment's split scaled down to refundedTotal, in the payment's order: tax from the total, then the fee from
     * the net. Both divisors are positive: refundedTotal is positive and at most the total, and a positive total
     * always has a net of at least one minor unit because the tax rate is below 1.
     */
    private static RefundAmounts cumulative(Payment payment, BigDecimal refundedTotal, int digits) {
        if (refundedTotal.compareTo(payment.totalAmount()) == 0) {
            return new RefundAmounts(payment.totalAmount(), payment.taxAmount(), payment.netAmount(),
                                     payment.feeAmount(), payment.merchantAmount());
        }
        BigDecimal tax = payment.taxAmount().multiply(refundedTotal)
                .divide(payment.totalAmount(), digits, RoundingMode.HALF_UP);
        BigDecimal net = refundedTotal.subtract(tax);
        BigDecimal fee = payment.feeAmount().multiply(net)
                .divide(payment.netAmount(), digits, RoundingMode.HALF_UP);
        return new RefundAmounts(refundedTotal, tax, net, fee, net.subtract(fee));
    }

    private RefundAmounts minus(RefundAmounts other) {
        return new RefundAmounts(totalAmount.subtract(other.totalAmount), taxAmount.subtract(other.taxAmount),
                                 netAmount.subtract(other.netAmount), feeAmount.subtract(other.feeAmount),
                                 merchantAmount.subtract(other.merchantAmount));
    }
}

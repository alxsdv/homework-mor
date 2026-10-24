package org.example.ledger.core.domain;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * What we owe a merchant in one currency, positive when we owe them, at the currency's scale: available can be paid
 * out now, pendingPayout is held for payouts in flight.
 */
public record MerchantPayable(String merchantId, Currency currency, BigDecimal available, BigDecimal pendingPayout) {

    /** Everything we owe the merchant. */
    public BigDecimal payable() {
        return available.add(pendingPayout);
    }
}

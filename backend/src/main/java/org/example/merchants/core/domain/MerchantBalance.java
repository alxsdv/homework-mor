package org.example.merchants.core.domain;

import java.math.BigDecimal;
import java.util.Currency;

/** What we owe a merchant in one currency: positive when we owe them, at the currency's scale. */
public record MerchantBalance(Currency currency, BigDecimal payable) {}

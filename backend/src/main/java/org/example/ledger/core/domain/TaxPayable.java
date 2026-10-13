package org.example.ledger.core.domain;

import java.math.BigDecimal;
import java.util.Currency;

/** What we owe a country's tax authority in one currency: positive when we owe it, at the currency's scale. */
public record TaxPayable(String countryCode, Currency currency, BigDecimal payable) {}

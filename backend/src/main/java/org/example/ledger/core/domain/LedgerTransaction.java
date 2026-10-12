package org.example.ledger.core.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One balanced ledger transaction: why (type), caused by what (sourceType + sourceId), when (effectiveAt),
 * and its entries. Entries share one currency and sum to zero.
 */
public record LedgerTransaction(String type, String sourceType, String sourceId, Instant effectiveAt,
                                List<LedgerEntry> entries) {

    public LedgerTransaction {
        entries = List.copyOf(entries);
        if (entries.size() < 2) {
            throw new IllegalArgumentException(
                    "A ledger transaction needs at least two entries, got " + entries.size());
        }
        Set<Currency> currencies = entries.stream()
                .map(entry -> entry.account().currency())
                .collect(Collectors.toSet());
        if (currencies.size() != 1) {
            throw new IllegalArgumentException(
                    "All entries of a ledger transaction must use one currency, got " + currencies);
        }
        BigDecimal sum = sum(entries);
        if (sum.signum() != 0) {
            throw new IllegalArgumentException(
                    "Ledger transaction entries must sum to zero, got " + sum + " for " + sourceId);
        }
    }

    public static BigDecimal sum(List<LedgerEntry> entries) {
        return entries.stream().map(LedgerEntry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

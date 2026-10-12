package org.example.ledger.core.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One balanced ledger transaction: why (type), caused by what (sourceType + sourceId), when (effectiveAt),
 * and its lines. Lines share one currency and sum to zero.
 */
public record Posting(String type, String sourceType, String sourceId, Instant effectiveAt,
                      List<PostingLine> lines) {

    public Posting {
        lines = List.copyOf(lines);
        if (lines.size() < 2) {
            throw new IllegalArgumentException("A posting needs at least two lines, got " + lines.size());
        }
        Set<Currency> currencies = lines.stream().map(line -> line.account().currency()).collect(Collectors.toSet());
        if (currencies.size() != 1) {
            throw new IllegalArgumentException("All lines of a posting must use one currency, got " + currencies);
        }
        BigDecimal sum = sum(lines);
        if (sum.signum() != 0) {
            throw new IllegalArgumentException("Posting lines must sum to zero, got " + sum + " for " + sourceId);
        }
    }

    public static BigDecimal sum(List<PostingLine> lines) {
        return lines.stream().map(PostingLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

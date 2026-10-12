package org.example.ledger.core.ports.out;

import java.math.BigDecimal;

/** A ledger entry to insert, with its account already resolved to an id. */
public record NewEntry(long accountId, BigDecimal amount) {}

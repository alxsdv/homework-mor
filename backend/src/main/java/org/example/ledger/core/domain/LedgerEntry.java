package org.example.ledger.core.domain;

import java.math.BigDecimal;

/** One signed entry of a ledger transaction: + debit, − credit. */
public record LedgerEntry(AccountKey account, BigDecimal amount) {}

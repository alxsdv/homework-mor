package org.example.ledger.core.domain;

import java.math.BigDecimal;

/** One signed line of a posting: + debit, − credit. */
public record PostingLine(AccountKey account, BigDecimal amount) {}

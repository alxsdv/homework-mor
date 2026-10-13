package org.example.merchants.core.domain;

import java.math.BigDecimal;

/** A merchant we settle payments for; feeRate is the share of each payment's net amount we keep. */
public record Merchant(String id, BigDecimal feeRate) {}

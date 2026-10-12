package org.example.payments.core.domain;

import java.math.BigDecimal;

public record Merchant(String id, BigDecimal feeRate) {}

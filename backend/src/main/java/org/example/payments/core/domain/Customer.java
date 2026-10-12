package org.example.payments.core.domain;

public record Customer(
        String firstName,
        String lastName,
        String email,
        Contact billingContact) {}

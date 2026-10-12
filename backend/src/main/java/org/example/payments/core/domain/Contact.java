package org.example.payments.core.domain;

public record Contact(
        String addressLine1,
        String addressLine2,
        String countryCode,
        String postalCode) {}

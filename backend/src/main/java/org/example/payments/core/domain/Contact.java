package org.example.payments.core.domain;

import org.jspecify.annotations.Nullable;

public record Contact(
        String addressLine1,
        @Nullable String addressLine2,
        String countryCode,
        String postalCode) {}

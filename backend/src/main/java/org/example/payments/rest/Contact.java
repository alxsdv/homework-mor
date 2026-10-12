package org.example.payments.rest;

import org.jspecify.annotations.Nullable;

public record Contact(
        String addressLine1,
        @Nullable String addressLine2,
        String countryCode,
        String postalCode) {

    public org.example.payments.core.domain.Contact toDomain() {
        return new org.example.payments.core.domain.Contact(addressLine1, addressLine2, countryCode, postalCode);
    }
}

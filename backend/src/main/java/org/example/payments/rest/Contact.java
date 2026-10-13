package org.example.payments.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import static org.example.payments.rest.RequestFields.required;

public record Contact(
        @Schema(requiredMode = REQUIRED, example = "Hauptstraße 1")
        @Nullable String addressLine1,
        @Schema(example = "Apartment 4")
        @Nullable String addressLine2,
        @Schema(requiredMode = REQUIRED, example = "DE", description = "ISO 3166-1 alpha-2 code")
        @Nullable String countryCode,
        @Schema(requiredMode = REQUIRED, example = "10115")
        @Nullable String postalCode) {

    public org.example.payments.core.domain.Contact toDomain() {
        return new org.example.payments.core.domain.Contact(
                required(addressLine1, "customer.billingContact.addressLine1"),
                addressLine2,
                required(countryCode, "customer.billingContact.countryCode"),
                required(postalCode, "customer.billingContact.postalCode"));
    }

    public static Contact fromDomain(org.example.payments.core.domain.Contact contact) {
        return new Contact(contact.addressLine1(), contact.addressLine2(), contact.countryCode(),
                           contact.postalCode());
    }
}

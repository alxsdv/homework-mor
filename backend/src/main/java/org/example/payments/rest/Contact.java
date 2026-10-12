package org.example.payments.rest;

import org.jspecify.annotations.Nullable;

import static org.example.payments.rest.RequestFields.required;

public record Contact(
        @Nullable String addressLine1,
        @Nullable String addressLine2,
        @Nullable String countryCode,
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

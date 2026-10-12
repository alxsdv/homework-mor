package org.example.payments.rest;

import org.jspecify.annotations.Nullable;

import static org.example.payments.rest.RequestFields.required;

public record Customer(
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable String email,
        @Nullable Contact billingContact) {

    public org.example.payments.core.domain.Customer toDomain() {
        return new org.example.payments.core.domain.Customer(
                required(firstName, "customer.firstName"),
                required(lastName, "customer.lastName"),
                required(email, "customer.email"),
                required(billingContact, "customer.billingContact").toDomain());
    }

    public static Customer fromDomain(org.example.payments.core.domain.Customer customer) {
        return new Customer(customer.firstName(), customer.lastName(), customer.email(),
                            Contact.fromDomain(customer.billingContact()));
    }
}

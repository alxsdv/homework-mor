package org.example.payments.rest;

public record Customer(
        String firstName,
        String lastName,
        String email,
        Contact billingContact) {

    public org.example.payments.core.domain.Customer toDomain() {
        return new org.example.payments.core.domain.Customer(firstName, lastName, email, billingContact.toDomain());
    }
}

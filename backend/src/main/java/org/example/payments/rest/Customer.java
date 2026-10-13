package org.example.payments.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import static org.example.payments.rest.RequestFields.required;

public record Customer(
        @Schema(requiredMode = REQUIRED, example = "Erika")
        @Nullable String firstName,
        @Schema(requiredMode = REQUIRED, example = "Mustermann")
        @Nullable String lastName,
        @Schema(requiredMode = REQUIRED, example = "erika@example.com")
        @Nullable String email,
        @Schema(requiredMode = REQUIRED)
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

package org.example.payments.core.domain;

import java.util.regex.Pattern;

public record PaymentId(String value) {
    private static final Pattern FORMAT = Pattern.compile("PAY-[0-9A-HJKMNP-TV-Z]{10}");

    public PaymentId {
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid Payment Id: " + value);
        }
    }
}

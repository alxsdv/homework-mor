package org.example.payments.core.domain;

import java.util.regex.Pattern;

public record RefundId(String value) {
    private static final Pattern FORMAT = Pattern.compile("REF-[0-9A-HJKMNP-TV-Z]{10}");

    public RefundId {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid Refund Id: " + value);
        }
    }
}

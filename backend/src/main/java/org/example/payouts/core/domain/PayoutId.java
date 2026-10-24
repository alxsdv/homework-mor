package org.example.payouts.core.domain;

import java.util.regex.Pattern;

public record PayoutId(String value) {
    private static final Pattern FORMAT = Pattern.compile("PO-[0-9A-HJKMNP-TV-Z]{10}");

    public PayoutId {
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid Payout Id: " + value);
        }
    }
}

package org.example.payouts.core.ports.in;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Shared by the payout commands. */
final class PayoutTimes {

    /** Within Postgres timestamptz range, which ends in year 294276. */
    private static final Instant MIN = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAX = Instant.parse("+10000-01-01T00:00:00Z");

    private PayoutTimes() {}

    /** Postgres timestamptz stores microseconds; truncating makes a resent value equal the stored one. */
    static Instant validated(Instant value, String field) {
        if (value.isBefore(MIN) || !value.isBefore(MAX)) {
            throw new InvalidPayoutException(field + " must be between years 0001 and 9999: " + value);
        }
        return value.truncatedTo(ChronoUnit.MICROS);
    }
}

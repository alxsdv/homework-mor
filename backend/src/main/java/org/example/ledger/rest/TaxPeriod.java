package org.example.ledger.rest;

import org.jspecify.annotations.Nullable;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/** A tax statement's period: from (inclusive) and to (exclusive) are dates in zone. */
record TaxPeriod(LocalDate from, LocalDate to, ZoneId zone) {

    /** ZoneId.of("UTC"), not ZoneOffset.UTC, whose id is "Z". */
    static final ZoneId DEFAULT_ZONE = ZoneId.of("UTC");

    /** Every message starts with the offending parameter's name. */
    static TaxPeriod parse(@Nullable String from, @Nullable String to, @Nullable String zone) {
        LocalDate fromDate = parseDate(from, "from");
        LocalDate toDate = parseDate(to, "to");
        ZoneId zoneId = zone == null ? DEFAULT_ZONE : parseZone(zone);
        if (!fromDate.isBefore(toDate)) {
            throw new InvalidTaxPeriodException(
                    "from (" + fromDate + ") must be before to (" + toDate + "); to is exclusive");
        }
        TaxPeriod period = new TaxPeriod(fromDate, toDate, zoneId);
        // A zone that skipped whole days maps both dates to one instant, leaving a period with nothing in it.
        if (!period.start().isBefore(period.end())) {
            throw new InvalidTaxPeriodException(
                    "from (" + fromDate + ") to (" + toDate + ") covers no time in " + zoneId.getId());
        }
        return period;
    }

    /** atStartOfDay, not atTime(00:00): on a day whose midnight a DST change skips, it picks the first instant. */
    Instant start() {
        return from.atStartOfDay(zone).toInstant();
    }

    Instant end() {
        return to.atStartOfDay(zone).toInstant();
    }

    private static LocalDate parseDate(@Nullable String value, String parameter) {
        if (value == null || value.isBlank()) {
            throw new InvalidTaxPeriodException(parameter + " is required, as an ISO date like 2026-07-01");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvalidTaxPeriodException(
                    parameter + " must be an ISO date like 2026-07-01, got: " + value);
        }
        // ISO allows +10000-01-01; years that far out overflow the database's timestamps.
        if (date.getYear() < 1 || date.getYear() > 9999) {
            throw new InvalidTaxPeriodException(
                    parameter + " must be between 0001-01-01 and 9999-12-31, got: " + value);
        }
        return date;
    }

    private static ZoneId parseZone(String value) {
        try {
            return ZoneId.of(value);
        } catch (DateTimeException e) {
            String hint = value.startsWith(" ") ? " (to send an offset like +02:00, encode + as %2B)" : "";
            throw new InvalidTaxPeriodException(
                    "zone must be a time zone ID like Europe/Berlin or +02:00, got: " + value + hint);
        }
    }
}

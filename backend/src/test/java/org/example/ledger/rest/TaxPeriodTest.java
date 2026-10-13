package org.example.ledger.rest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaxPeriodTest {

    @Test
    void withoutZoneTheDatesAreUtc() {
        TaxPeriod period = TaxPeriod.parse("2026-07-01", "2026-10-01", null);

        assertThat(period.start()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
        assertThat(period.end()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(period.zone().getId()).isEqualTo("UTC");
    }

    @Test
    void datesAreInTheGivenZone() {
        TaxPeriod period = TaxPeriod.parse("2026-07-01", "2026-10-01", "Europe/Berlin");

        assertThat(period.start()).isEqualTo(Instant.parse("2026-06-30T22:00:00Z"));
        assertThat(period.end()).isEqualTo(Instant.parse("2026-09-30T22:00:00Z"));
        assertThat(period.zone().getId()).isEqualTo("Europe/Berlin");
    }

    @Test
    void offsetZoneIsAcceptedAndKeepsItsId() {
        TaxPeriod period = TaxPeriod.parse("2026-07-01", "2026-10-01", "+02:00");

        assertThat(period.start()).isEqualTo(Instant.parse("2026-06-30T22:00:00Z"));
        assertThat(period.zone().getId()).isEqualTo("+02:00");
    }

    @Test
    void dayWhoseMidnightIsSkippedByDstStartsAtItsFirstInstant() {
        // Chile moves its clocks from 00:00 to 01:00 on 2026-09-06.
        TaxPeriod period = TaxPeriod.parse("2026-09-06", "2026-09-07", "America/Santiago");

        assertThat(period.start()).isEqualTo(Instant.parse("2026-09-06T04:00:00Z"));
    }

    @Test
    void fromMustBeBeforeTo() {
        assertThatThrownBy(() -> TaxPeriod.parse("2026-07-01", "2026-07-01", null))
                .isInstanceOf(InvalidTaxPeriodException.class)
                .hasMessageContaining("from")
                .hasMessageContaining("exclusive");
        assertThatThrownBy(() -> TaxPeriod.parse("2026-10-01", "2026-07-01", null))
                .isInstanceOf(InvalidTaxPeriodException.class);
    }

    @Test
    void periodOfOnlyASkippedDayIsRejected() {
        // Samoa skipped 2011-12-30 when it moved across the date line, so this period has no instants in it.
        assertThatThrownBy(() -> TaxPeriod.parse("2011-12-30", "2011-12-31", "Pacific/Apia"))
                .isInstanceOf(InvalidTaxPeriodException.class)
                .hasMessageContaining("Pacific/Apia")
                .hasMessageContaining("no time");
    }

    @Test
    void fromIsRequired() {
        assertThatThrownBy(() -> TaxPeriod.parse(null, "2026-10-01", null))
                .isInstanceOf(InvalidTaxPeriodException.class)
                .hasMessageStartingWith("from ");
        assertThatThrownBy(() -> TaxPeriod.parse("", "2026-10-01", null))
                .isInstanceOf(InvalidTaxPeriodException.class)
                .hasMessageStartingWith("from ");
    }

    @Test
    void toIsRequired() {
        assertThatThrownBy(() -> TaxPeriod.parse("2026-07-01", null, null))
                .isInstanceOf(InvalidTaxPeriodException.class)
                .hasMessageStartingWith("to ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-7-1", "01.07.2026", "2026-02-30", "yesterday"})
    void rejectsMalformedDate(String to) {
        assertThatThrownBy(() -> TaxPeriod.parse("2026-01-01", to, null))
                .isInstanceOf(InvalidTaxPeriodException.class)
                .hasMessageStartingWith("to ")
                .hasMessageContaining(to);
    }

    @Test
    void rejectsDateWithTime() {
        assertThatThrownBy(() -> TaxPeriod.parse("2026-07-01T00:00:00Z", "2026-10-01", null))
                .isInstanceOf(InvalidTaxPeriodException.class)
                .hasMessageStartingWith("from ");
    }

    @Test
    void rejectsYearsOutsideFourDigits() {
        assertThatThrownBy(() -> TaxPeriod.parse("2026-07-01", "+10000-01-01", null))
                .isInstanceOf(InvalidTaxPeriodException.class)
                .hasMessageStartingWith("to ");
        assertThatThrownBy(() -> TaxPeriod.parse("0000-12-31", "2026-07-01", null))
                .isInstanceOf(InvalidTaxPeriodException.class)
                .hasMessageStartingWith("from ");
    }

    @Test
    void acceptsTheWholeFourDigitRange() {
        TaxPeriod period = TaxPeriod.parse("0001-01-01", "9999-12-31", null);

        assertThat(period.start()).isEqualTo(Instant.parse("0001-01-01T00:00:00Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Mars/Olympus", "utc", "europe/berlin", "+25:00"})
    void rejectsUnknownZone(String zone) {
        assertThatThrownBy(() -> TaxPeriod.parse("2026-07-01", "2026-10-01", zone))
                .isInstanceOf(InvalidTaxPeriodException.class)
                .hasMessageStartingWith("zone ")
                .hasMessageContaining(zone);
    }

    @Test
    void rejectsEmptyZone() {
        assertThatThrownBy(() -> TaxPeriod.parse("2026-07-01", "2026-10-01", ""))
                .isInstanceOf(InvalidTaxPeriodException.class)
                .hasMessageStartingWith("zone ");
    }

    @Test
    void unencodedPlusInZoneExplainsEncoding() {
        // "zone=+02:00" sent without encoding arrives as " 02:00".
        assertThatThrownBy(() -> TaxPeriod.parse("2026-07-01", "2026-10-01", " 02:00"))
                .isInstanceOf(InvalidTaxPeriodException.class)
                .hasMessageContaining("%2B");
    }
}

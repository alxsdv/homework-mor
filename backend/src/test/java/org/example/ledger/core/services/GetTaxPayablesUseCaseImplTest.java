package org.example.ledger.core.services;

import org.example.ledger.core.domain.MerchantPayable;
import org.example.ledger.core.domain.TaxPayable;
import org.example.ledger.core.ports.out.LedgerQueries;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetTaxPayablesUseCaseImplTest {

    private static final Instant JULY = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant OCTOBER = Instant.parse("2026-10-01T00:00:00Z");
    private static final TaxPayable DE = new TaxPayable("DE", Currency.getInstance("EUR"), new BigDecimal("19.00"));

    private final List<List<Instant>> queriedPeriods = new ArrayList<>();
    private final GetTaxPayablesUseCaseImpl useCase = new GetTaxPayablesUseCaseImpl(new LedgerQueries() {
        @Override
        public List<MerchantPayable> merchantPayables(String merchantId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TaxPayable> taxPayables(Instant from, Instant to) {
            queriedPeriods.add(List.of(from, to));
            return List.of(DE);
        }
    });

    @Test
    void returnsTheLedgersTaxPayablesForThePeriod() {
        assertThat(useCase.getTaxPayables(JULY, OCTOBER)).containsExactly(DE);
        assertThat(queriedPeriods).containsExactly(List.of(JULY, OCTOBER));
    }

    @Test
    void rejectsEmptyPeriod() {
        assertThatThrownBy(() -> useCase.getTaxPayables(JULY, JULY)).isInstanceOf(IllegalArgumentException.class);
        assertThat(queriedPeriods).isEmpty();
    }

    @Test
    void rejectsReversedPeriod() {
        assertThatThrownBy(() -> useCase.getTaxPayables(OCTOBER, JULY)).isInstanceOf(IllegalArgumentException.class);
        assertThat(queriedPeriods).isEmpty();
    }
}

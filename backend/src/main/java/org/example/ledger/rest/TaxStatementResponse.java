package org.example.ledger.rest;

import org.example.ledger.core.domain.TaxPayable;

import java.util.List;

/** 200 body for GET /tax-statement: the period as requested, and amounts as strings at the currency's scale. */
public record TaxStatementResponse(String from, String to, String zone, List<Statement> statements) {

    /** What we owe one country's tax authority in one currency. */
    public record Statement(String countryCode, String currency, String payable) {}

    static TaxStatementResponse fromDomain(TaxPeriod period, List<TaxPayable> payables) {
        return new TaxStatementResponse(
                period.from().toString(),
                period.to().toString(),
                period.zone().getId(),
                payables.stream()
                        .map(payable -> new Statement(payable.countryCode(), payable.currency().getCurrencyCode(),
                                                      payable.payable().toPlainString()))
                        .toList());
    }
}

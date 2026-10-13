package org.example.ledger.rest;

/** A tax statement's from, to or zone parameter is missing or malformed. Maps to 400. */
public class InvalidTaxPeriodException extends RuntimeException {

    public InvalidTaxPeriodException(String message) {
        super(message);
    }
}

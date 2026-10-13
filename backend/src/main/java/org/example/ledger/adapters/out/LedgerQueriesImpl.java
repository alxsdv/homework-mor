package org.example.ledger.adapters.out;

import org.example.ledger.core.domain.AccountType;
import org.example.ledger.core.domain.MerchantPayable;
import org.example.ledger.core.domain.TaxPayable;
import org.example.ledger.core.ports.out.LedgerQueries;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;

import static org.example.jooq.Tables.LEDGER_ACCOUNTS;
import static org.example.jooq.Tables.LEDGER_ENTRIES;
import static org.example.jooq.Tables.LEDGER_TRANSACTIONS;
import static org.jooq.impl.DSL.sum;

@Repository
public class LedgerQueriesImpl implements LedgerQueries {

    private final DSLContext dsl;

    public LedgerQueriesImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Liabilities are stored negative, so flip the sign. numeric(19,4) comes back as 97.0000: every posted amount
     * is at the currency's scale, so rescaling is exact, and a value that isn't means corrupt data.
     */
    private static BigDecimal payable(BigDecimal balance, Currency currency) {
        return balance.negate().setScale(currency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY);
    }

    @Override
    public List<MerchantPayable> merchantPayables(String merchantId) {
        return dsl.select(LEDGER_ACCOUNTS.CURRENCY, LEDGER_ACCOUNTS.BALANCE)
                .from(LEDGER_ACCOUNTS)
                .where(LEDGER_ACCOUNTS.TYPE.eq(AccountType.MERCHANT_PAYABLE.name()))
                .and(LEDGER_ACCOUNTS.MERCHANT_ID.eq(merchantId))
                .orderBy(LEDGER_ACCOUNTS.CURRENCY)
                .fetch(record -> {
                    Currency currency = Currency.getInstance(record.value1());
                    return new MerchantPayable(merchantId, currency, payable(record.value2(), currency));
                });
    }

    @Override
    public List<TaxPayable> taxPayables(Instant from, Instant to) {
        Field<BigDecimal> total = sum(LEDGER_ENTRIES.AMOUNT);
        return dsl.select(LEDGER_ACCOUNTS.COUNTRY_CODE, LEDGER_ACCOUNTS.CURRENCY, total)
                .from(LEDGER_ENTRIES)
                .join(LEDGER_ACCOUNTS).on(LEDGER_ACCOUNTS.ID.eq(LEDGER_ENTRIES.ACCOUNT_ID))
                .join(LEDGER_TRANSACTIONS).on(LEDGER_TRANSACTIONS.ID.eq(LEDGER_ENTRIES.TRANSACTION_ID))
                .where(LEDGER_ACCOUNTS.TYPE.eq(AccountType.TAX_PAYABLE.name()))
                .and(LEDGER_TRANSACTIONS.EFFECTIVE_AT.ge(from.atOffset(ZoneOffset.UTC)))
                .and(LEDGER_TRANSACTIONS.EFFECTIVE_AT.lt(to.atOffset(ZoneOffset.UTC)))
                .groupBy(LEDGER_ACCOUNTS.COUNTRY_CODE, LEDGER_ACCOUNTS.CURRENCY)
                .orderBy(LEDGER_ACCOUNTS.COUNTRY_CODE, LEDGER_ACCOUNTS.CURRENCY)
                .fetch(record -> {
                    Currency currency = Currency.getInstance(record.value2());
                    return new TaxPayable(record.value1(), currency, payable(record.value3(), currency));
                });
    }
}

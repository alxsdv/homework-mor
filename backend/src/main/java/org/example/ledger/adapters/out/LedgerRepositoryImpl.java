package org.example.ledger.adapters.out;

import org.example.jooq.tables.records.LedgerEntriesRecord;
import org.example.ledger.core.domain.AccountKey;
import org.example.ledger.core.domain.LedgerTransaction;
import org.example.ledger.core.ports.out.LedgerRepository;
import org.example.ledger.core.ports.out.NewEntry;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep3;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.List;

import static org.example.jooq.Tables.LEDGER_ACCOUNTS;
import static org.example.jooq.Tables.LEDGER_ENTRIES;
import static org.example.jooq.Tables.LEDGER_TRANSACTIONS;

@Repository
public class LedgerRepositoryImpl implements LedgerRepository {

    private final DSLContext dsl;

    public LedgerRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public long findOrCreateAccount(AccountKey key) {
        BigDecimal initialBalance = key.type().hasCachedBalance() ? BigDecimal.ZERO : null;
        // A concurrent creator makes us wait, then DO NOTHING; the SELECT below then sees its committed row.
        dsl.insertInto(LEDGER_ACCOUNTS)
                .set(LEDGER_ACCOUNTS.TYPE, key.type().name())
                .set(LEDGER_ACCOUNTS.CURRENCY, key.currency().getCurrencyCode())
                .set(LEDGER_ACCOUNTS.MERCHANT_ID, key.merchantId())
                .set(LEDGER_ACCOUNTS.COUNTRY_CODE, key.countryCode())
                .set(LEDGER_ACCOUNTS.BALANCE, initialBalance)
                .onConflictDoNothing()
                .execute();
        Long id = dsl.select(LEDGER_ACCOUNTS.ID)
                .from(LEDGER_ACCOUNTS)
                .where(LEDGER_ACCOUNTS.TYPE.eq(key.type().name()))
                .and(LEDGER_ACCOUNTS.CURRENCY.eq(key.currency().getCurrencyCode()))
                .and(LEDGER_ACCOUNTS.MERCHANT_ID.isNotDistinctFrom(key.merchantId()))
                .and(LEDGER_ACCOUNTS.COUNTRY_CODE.isNotDistinctFrom(key.countryCode()))
                .fetchSingle(LEDGER_ACCOUNTS.ID);
        if (id == null) {
            throw new IllegalStateException("Account " + key + " has no id");
        }
        return id;
    }

    @Override
    public BigDecimal lockCachedAccount(long accountId) {
        BigDecimal balance = dsl.select(LEDGER_ACCOUNTS.BALANCE)
                .from(LEDGER_ACCOUNTS)
                .where(LEDGER_ACCOUNTS.ID.eq(accountId))
                .forUpdate()
                .fetchSingle(LEDGER_ACCOUNTS.BALANCE);
        if (balance == null) {
            throw new IllegalStateException("Account " + accountId + " has no cached balance");
        }
        return balance;
    }

    @Override
    public long insertTransaction(LedgerTransaction transaction) {
        return dsl.insertInto(LEDGER_TRANSACTIONS)
                .set(LEDGER_TRANSACTIONS.TYPE, transaction.type())
                .set(LEDGER_TRANSACTIONS.SOURCE_TYPE, transaction.sourceType())
                .set(LEDGER_TRANSACTIONS.SOURCE_ID, transaction.sourceId())
                .set(LEDGER_TRANSACTIONS.EFFECTIVE_AT, transaction.effectiveAt().atOffset(ZoneOffset.UTC))
                .returning(LEDGER_TRANSACTIONS.ID)
                .fetchSingle()
                .getId();
    }

    @Override
    public void insertEntries(long transactionId, List<NewEntry> entries) {
        InsertValuesStep3<LedgerEntriesRecord, Long, Long, BigDecimal> insert = dsl.insertInto(
                LEDGER_ENTRIES, LEDGER_ENTRIES.TRANSACTION_ID, LEDGER_ENTRIES.ACCOUNT_ID, LEDGER_ENTRIES.AMOUNT);
        for (NewEntry entry : entries) {
            insert = insert.values(transactionId, entry.accountId(), entry.amount());
        }
        insert.execute();
    }

    @Override
    public void updateBalance(long accountId, BigDecimal newBalance) {
        dsl.update(LEDGER_ACCOUNTS)
                .set(LEDGER_ACCOUNTS.BALANCE, newBalance)
                .where(LEDGER_ACCOUNTS.ID.eq(accountId))
                .execute();
    }
}

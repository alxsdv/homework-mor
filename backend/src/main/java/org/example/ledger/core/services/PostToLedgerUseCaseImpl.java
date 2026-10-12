package org.example.ledger.core.services;

import org.example.ledger.core.domain.AccountKey;
import org.example.ledger.core.domain.LedgerEntry;
import org.example.ledger.core.domain.LedgerTransaction;
import org.example.ledger.core.ports.in.PostToLedgerUseCase;
import org.example.ledger.core.ports.out.LedgerRepository;
import org.example.ledger.core.ports.out.NewEntry;
import org.example.shared.transactions.TransactionRunner;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PostToLedgerUseCaseImpl implements PostToLedgerUseCase {

    private final LedgerRepository ledgerRepository;
    private final TransactionRunner transactionRunner;

    public PostToLedgerUseCaseImpl(LedgerRepository ledgerRepository, TransactionRunner transactionRunner) {
        this.ledgerRepository = ledgerRepository;
        this.transactionRunner = transactionRunner;
    }

    private static long accountIdOf(Map<AccountKey, Long> accountIds, AccountKey key) {
        Long id = accountIds.get(key);
        if (id == null) {
            throw new IllegalStateException("Account was not resolved: " + key);
        }
        return id;
    }

    @Override
    public void post(LedgerTransaction transaction) {
        // Explicit transaction: joins the payment's transaction when called from it.
        transactionRunner.inTransaction(() -> postInCurrentTransaction(transaction));
    }

    private void postInCurrentTransaction(LedgerTransaction transaction) {
        // Step 1 (backstop): LedgerTransaction's constructor already rejects unbalanced entries.
        if (LedgerTransaction.sum(transaction.entries()).signum() != 0) {
            throw new IllegalArgumentException("Ledger transaction " + transaction.sourceId() + " is not balanced");
        }

        // Step 2: get or create every account the transaction touches.
        Map<AccountKey, Long> accountIds = new LinkedHashMap<>();
        for (LedgerEntry entry : transaction.entries()) {
            if (!accountIds.containsKey(entry.account())) {
                accountIds.put(entry.account(), ledgerRepository.findOrCreateAccount(entry.account()));
            }
        }

        // Step 3: lock the accounts with a cached balance, in ascending id order to rule out deadlocks.
        List<Long> cachedAccountIds = accountIds.entrySet().stream()
                .filter(entry -> entry.getKey().type().hasCachedBalance())
                .map(Map.Entry::getValue)
                .sorted()
                .toList();
        Map<Long, BigDecimal> lockedBalances = new LinkedHashMap<>();
        for (Long accountId : cachedAccountIds) {
            lockedBalances.put(accountId, ledgerRepository.lockCachedAccount(accountId));
        }

        // Step 4: write the transaction and its entries.
        long transactionId = ledgerRepository.insertTransaction(transaction);
        List<NewEntry> newEntries = transaction.entries().stream()
                .map(entry -> new NewEntry(accountIdOf(accountIds, entry.account()), entry.amount()))
                .toList();
        ledgerRepository.insertEntries(transactionId, newEntries);

        // Step 5: move each locked balance by the sum of its entries; safe because we hold the row lock.
        lockedBalances.forEach((accountId, balance) -> {
            BigDecimal delta = newEntries.stream()
                    .filter(entry -> entry.accountId() == accountId)
                    .map(NewEntry::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            ledgerRepository.updateBalance(accountId, balance.add(delta));
        });
    }
}

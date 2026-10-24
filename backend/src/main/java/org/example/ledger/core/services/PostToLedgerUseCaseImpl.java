package org.example.ledger.core.services;

import org.example.ledger.core.domain.AccountKey;
import org.example.ledger.core.domain.LedgerEntry;
import org.example.ledger.core.domain.LedgerTransaction;
import org.example.ledger.core.ports.in.InsufficientFundsException;
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

    private static BigDecimal sumFor(List<NewEntry> entries, long accountId) {
        return entries.stream()
                .filter(entry -> entry.accountId() == accountId)
                .map(NewEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public void post(LedgerTransaction transaction) {
        // Explicit transaction: joins the payment's transaction when called from it.
        transactionRunner.inTransaction(() -> postInCurrentTransaction(transaction, false));
    }

    @Override
    public void postWithinAvailableFunds(LedgerTransaction transaction) {
        transactionRunner.inTransaction(() -> postInCurrentTransaction(transaction, true));
    }

    private void postInCurrentTransaction(LedgerTransaction transaction, boolean checkFunds) {
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
        List<NewEntry> newEntries = transaction.entries().stream()
                .map(entry -> new NewEntry(accountIdOf(accountIds, entry.account()), entry.amount()))
                .toList();

        // Step 3: lock the accounts with a cached balance, in ascending id order to rule out deadlocks, and work
        // out their new balances. A funds check made here can't go stale: nothing else moves them until commit.
        List<Map.Entry<AccountKey, Long>> cachedAccounts = accountIds.entrySet().stream()
                .filter(entry -> entry.getKey().type().hasCachedBalance())
                .sorted(Map.Entry.comparingByValue())
                .toList();
        Map<Long, BigDecimal> newBalances = new LinkedHashMap<>();
        for (Map.Entry<AccountKey, Long> account : cachedAccounts) {
            long accountId = account.getValue();
            BigDecimal balance = ledgerRepository.lockCachedAccount(accountId);
            BigDecimal delta = sumFor(newEntries, accountId);
            BigDecimal newBalance = balance.add(delta);
            // What we owe is stored negative, so a debit that leaves it above zero means the merchant owes us.
            if (checkFunds && delta.signum() > 0 && newBalance.signum() > 0) {
                throw new InsufficientFundsException(account.getKey(), balance.negate(), delta);
            }
            newBalances.put(accountId, newBalance);
        }

        // Step 4: write the transaction and its entries.
        long transactionId = ledgerRepository.insertTransaction(transaction);
        ledgerRepository.insertEntries(transactionId, newEntries);

        // Step 5: store the new balances; safe because we hold the row locks.
        newBalances.forEach(ledgerRepository::updateBalance);
    }
}

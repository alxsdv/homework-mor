-- How much we owe each merchant, per currency.
-- Reads the cached balance; liabilities are stored negative, so the sign is flipped.
SELECT merchant_id, currency, -balance AS owed
FROM ledger_accounts
WHERE type = 'MERCHANT_PAYABLE'
ORDER BY merchant_id, currency;

-- How much tax we owe each country, per currency, for a period.
-- Bind parameters, in order: period start (inclusive), period end (exclusive), both timestamptz.
-- effective_at is the settledAt of the payment, compared as an instant: pick period bounds in the zone you report in.
-- Keep question marks and quotes out of these comments: jOOQ scans plain SQL for bind markers.
SELECT a.country_code, a.currency, -SUM(e.amount) AS owed
FROM ledger_entries e
         JOIN ledger_accounts a ON a.id = e.account_id
         JOIN ledger_transactions t ON t.id = e.transaction_id
WHERE a.type = 'TAX_PAYABLE'
  AND t.effective_at >= CAST(? AS timestamptz)
  AND t.effective_at < CAST(? AS timestamptz)
GROUP BY a.country_code, a.currency
ORDER BY a.country_code, a.currency;

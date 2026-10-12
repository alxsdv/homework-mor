CREATE TABLE ledger_accounts
(
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type         text           NOT NULL,
    currency     char(3)        NOT NULL,
    merchant_id  text           NULL,
    country_code char(2)        NULL,
    -- Cached SUM(ledger_entries.amount); only MERCHANT_PAYABLE accounts keep one.
    balance      numeric(19, 4) NULL,
    CONSTRAINT ledger_accounts_key UNIQUE NULLS NOT DISTINCT (type, currency, merchant_id, country_code),
    CONSTRAINT ledger_accounts_key_parts CHECK (
        (type IN ('CASH_CLEARING', 'FEE_REVENUE') AND merchant_id IS NULL AND country_code IS NULL) OR
        (type = 'MERCHANT_PAYABLE' AND merchant_id IS NOT NULL AND country_code IS NULL) OR
        (type = 'TAX_PAYABLE' AND merchant_id IS NULL AND country_code IS NOT NULL)),
    CONSTRAINT ledger_accounts_cached_balance CHECK ((balance IS NOT NULL) = (type = 'MERCHANT_PAYABLE'))
);

CREATE TABLE ledger_transactions
(
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type         text        NOT NULL,
    source_type  text        NOT NULL,
    source_id    text        NOT NULL,
    effective_at timestamptz NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ledger_transactions_source_key UNIQUE (type, source_type, source_id)
);

-- Append-only: + debit, − credit; the entries of one transaction sum to zero.
CREATE TABLE ledger_entries
(
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id bigint         NOT NULL REFERENCES ledger_transactions (id),
    account_id     bigint         NOT NULL REFERENCES ledger_accounts (id),
    amount         numeric(19, 4) NOT NULL
);

CREATE INDEX ledger_entries_account_idx ON ledger_entries (account_id);
CREATE INDEX ledger_entries_transaction_idx ON ledger_entries (transaction_id);

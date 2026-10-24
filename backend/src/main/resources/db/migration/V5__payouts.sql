-- Money reserved for a payout in flight: still owed to the merchant, but no longer available to pay out again.
ALTER TABLE ledger_accounts
    DROP CONSTRAINT ledger_accounts_key_parts,
    ADD CONSTRAINT ledger_accounts_key_parts CHECK (
        (type IN ('CASH_CLEARING', 'FEE_REVENUE') AND merchant_id IS NULL AND country_code IS NULL) OR
        (type IN ('MERCHANT_PAYABLE', 'MERCHANT_PAYOUT_PENDING') AND merchant_id IS NOT NULL
            AND country_code IS NULL) OR
        (type = 'TAX_PAYABLE' AND merchant_id IS NULL AND country_code IS NOT NULL));

-- One row per payout to a merchant. PENDING holds the amount on the ledger; it then ends either SETTLED (the money
-- left our bank account) or FAILED (the hold is released). completed_at is set exactly when it has ended.
CREATE TABLE payouts
(
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id    text           NOT NULL CONSTRAINT payouts_public_id_key UNIQUE,
    merchant_id  text           NOT NULL REFERENCES merchants (public_id),
    external_id  text           NOT NULL,
    currency     char(3)        NOT NULL,
    amount       numeric(19, 4) NOT NULL CHECK (amount > 0),
    requested_at timestamptz    NOT NULL,
    status       text           NOT NULL CHECK (status IN ('PENDING', 'SETTLED', 'FAILED')),
    completed_at timestamptz    NULL,
    created_at   timestamptz    NOT NULL DEFAULT now(),
    CONSTRAINT payouts_merchant_id_external_id_key UNIQUE (merchant_id, external_id),
    CONSTRAINT payouts_completed_at_matches_status CHECK ((completed_at IS NULL) = (status = 'PENDING'))
);

-- One row per refund settled outside the system. Append-only: what's left to refund is the payment's total
-- minus the sum of its refunds. The payment's currency, tax country and rates are copied in, so a refund reads
-- on its own.
CREATE TABLE refunds
(
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id       text           NOT NULL CONSTRAINT refunds_public_id_key UNIQUE,
    payment_id      text           NOT NULL REFERENCES payments (public_id),
    merchant_id     text           NOT NULL REFERENCES merchants (public_id),
    external_id     text           NOT NULL,
    refunded_at     timestamptz    NOT NULL,
    -- Requested without an amount; an idempotent repeat must also come without one.
    full_refund     boolean        NOT NULL,
    tax_country     char(2)        NOT NULL,
    currency        char(3)        NOT NULL,
    tax_rate        numeric(9, 6)  NOT NULL,
    fee_rate        numeric(9, 6)  NOT NULL,
    total_amount    numeric(19, 4) NOT NULL CHECK (total_amount > 0),
    tax_amount      numeric(19, 4) NOT NULL,
    net_amount      numeric(19, 4) NOT NULL,
    fee_amount      numeric(19, 4) NOT NULL,
    merchant_amount numeric(19, 4) NOT NULL,
    created_at      timestamptz    NOT NULL DEFAULT now(),
    CONSTRAINT refunds_merchant_id_external_id_key UNIQUE (merchant_id, external_id)
);

CREATE INDEX refunds_payment_idx ON refunds (payment_id);

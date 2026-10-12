CREATE TABLE merchants
(
    id       text PRIMARY KEY,
    fee_rate numeric(9, 6) NOT NULL CHECK (fee_rate >= 0 AND fee_rate < 1)
);

CREATE TABLE payments
(
    id                    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id             text           NOT NULL CONSTRAINT payments_public_id_key UNIQUE,
    merchant_id           text           NOT NULL REFERENCES merchants (id),
    external_id           text           NOT NULL,
    settled_at            timestamptz    NOT NULL,
    customer_first_name   text           NOT NULL,
    customer_last_name    text           NOT NULL,
    customer_email        text           NOT NULL,
    billing_address_line1 text           NOT NULL,
    billing_address_line2 text           NULL,
    billing_country_code  char(2)        NOT NULL,
    billing_postal_code   text           NOT NULL,
    tax_country           char(2)        NOT NULL,
    currency              char(3)        NOT NULL,
    total_amount          numeric(19, 4) NOT NULL,
    tax_rate              numeric(9, 6)  NOT NULL,
    tax_amount            numeric(19, 4) NOT NULL,
    net_amount            numeric(19, 4) NOT NULL,
    fee_rate              numeric(9, 6)  NOT NULL,
    fee_amount            numeric(19, 4) NOT NULL,
    merchant_amount       numeric(19, 4) NOT NULL,
    created_at            timestamptz    NOT NULL DEFAULT now(),
    CONSTRAINT payments_merchant_id_external_id_key UNIQUE (merchant_id, external_id)
);

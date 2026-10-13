# Homework MoR Ledger Service Design

## Context & Problem Statement

A Merchant of Record (MoR) is the seller of record:
it collects payment from the customer, is liable for remitting sales tax/VAT to the relevant tax authority,
takes its own fee, and owes the remainder to the merchant.

The goal was to build a small (:shrug:) MoR ledger service exposing APIs to:

1. **Capture a payment** — Record that a customer payment was captured for a given merchant.
2. **Refund a payment** — Record a refund against a previously captured payment.
3. **Balances Report** —
    - How much does the MoR owe tax authorities?
    - What are merchants' balances with the MoR?

Additional assumptions from the task:

- MoR takes a single flat % fee from its customers (*Merchants*)
- Tax Rate is communicated in the captured payment payload
- Multi-currency is a nice-to-have
- Partial refunds are a nice-to-have

## Solution Design

### Requirements analysis

The main flow requested in the requirements: recording that a customer payment was captured for a given merchant.  

The assumptions and conclusions that affected the application design:

1. We only **record** that a payment has been already captured — the actual payment processing happens outside our system;

2. In general, we may assume that since the payment was already captured — it has been already calculated properly.  
  I.e. if this was a payment for a sale, some another system *already calculated* the total for the payment,  
  taking into account individual line items, unit price, quantities, and even taxes — we get only the `totalAmount`  
  and the `taxRate` that was used for calculations.

3. Taking into account the line on taxes from the task (`Tax Rate is communicated in the captured payment payload`),
it was decided to approach taxes with __*!great simplification!*__ — that one `taxRate` is enough to represent and calculate  
Tax Amount paid by the customer.  
The author *knows* (*heard*, rather) that taxes can be multi-leveled, have different applicability logic (included or excluded),
but this complexity was left out of scope. I'm sorry if that is unacceptable.

4. Also, knowing that multi-currency is a nice-to-have feature, I've tried to build it in initially.  
  It gave me the hint that:
    - There could be customers paying to the same Merchant in different currencies;
    - And as a consequence, we (the MoR) will need to pay the taxes in different countries (jurisdictions);

5. To support multi-currency, it was decided that one Merchant may have **>1 balance** in the system,  
  per the number of currencies from the recorded payments.   
  For tax statements, though, only grouping per currency is not enough, since each country wants its taxes separately,  
  even if the currency matches between countries (hello, European Union).

### Architecture

The application is a modular monolith, consisting of 3 modules:

- Merchants
- Payments (w/Refunds)
- Ledger

Each module is structured in accordance with the Hexagonal architectural style:

  - The inner *core* of the module contains the domain entities and business logic, frameworks-agnostic.
  - The inner core defines *ports* (interfaces), that the outer *adapters* implement.
  - Modules can call the logic from another module through the port, defined in the core, and implemented by the adapter.
  - There is no shared persistence or controllers layer, each module contains its own stuff.
  - Modules boundaries are enforced with the help of *ArchUnit* tests.

Modules dependencies are:

```
payments ──▶ merchants ──▶ ledger
    └──────────────────────▶ ledger
```

1. The "Ledger" module is responsible for storing ledger accounts and recording transactions against them (double-legged ledger transactions).  
  It does not depend on any module.
  All balances accumulate in this module.
2. "Merchants" module store the information about Merchants (like their fee with MoR, for example), and communicate with
the "Ledger" module to get the Merchant's balances with the MoR.
3. "Payments" module depends on both "Merchants" and "Ledger" modules.  
  This module captures Payments and Refunds from the REST API.
  The information about the Merchant (e.g. the MoR fee rate) is requested from the "Merchants" module.
  Total Amount of a Payment is then split between the Merchant, Tax Authorities and our own Revenue (*Ledger Transaction Entries*).
  The payment transaction (and all the lines) are posted to the "Ledger".

#### Communications

To achieve strong consistency, the "Payments" module stores Payment/Refund and posts to the "Ledger" module synchronously
in scope of one DB transaction.

### Data model

![erd.drawio.png](erd.drawio.png)

### Business logic

#### Payment and Refund amounts split

All amounts are rounded to the currency's decimal places.

A Payment's `totalAmount` is split as follows:

- `net = total / (1 + taxRate)`, rounded half-up;
- `tax = total − net`;
- `fee = net × feeRate`, rounded half-up;
- `merchant = net − fee`.

Rounding remainders go to the merchant, so `tax + fee + merchant = total` always holds.

A Refund is split using the Payment's own split, scaled to everything refunded so far (`refundedTotal`), including this refund:

- `tax = payment tax × refundedTotal / payment total`, rounded half-up;
- `net = refundedTotal − tax`;
- `fee = payment fee × net / payment net`, rounded half-up;
- `merchant = net − fee`.

The Refund's split is that cumulative split minus the sum of the previous Refunds' splits.
Rounding never accumulates, and the Refunds of a fully refunded Payment add up to exactly the Payment's split.

#### Ledger accounts and reporting

The Ledger is a double-entry ledger made of three tables:

- `ledger_accounts`: one account per type and key:
    - *Cash Clearing* (per currency): money the MoR holds;
    - *Fee Revenue* (per currency): fees the MoR earned;
    - *Tax Payable* (per currency + country): what the MoR owes a tax authority;
    - *Merchant Payable* (per currency + merchant): what the MoR owes a merchant.
- `ledger_transactions`: one row per business event (`PAYMENT_SETTLED`, `PAYMENT_REFUNDED`),
  pointing back to the Payment or Refund that caused it, with its `effective_at` time.
- `ledger_entries`: the lines of a transaction, each moving an amount on one account.
  A positive amount is a debit, a negative one is a credit, and the entries of one transaction always sum to zero.

Entries are append-only: nothing is ever updated or deleted, a Refund is recorded as a new transaction
with the Payment's lines reversed. Any balance is therefore just the sum of the account's entries.
Liabilities (*Tax Payable*, *Merchant Payable*) and revenue end up with negative balances, so reports flip the sign.

Accounts are created on first use. Only *Merchant Payable* accounts keep a cached `balance` column,
updated under a row lock in the same transaction that posts the entries.
The other accounts are shared by all merchants and would become hot rows, so their balances are summed on demand.

##### Example

Merchant `MERCH-001` (3% fee) records a payment of `119.00 EUR` from a German customer at a 19% tax rate,
then a partial refund of `59.50 EUR`:

- Payment: `net = 119.00 / 1.19 = 100.00`, `tax = 19.00`, `fee = 3.00`, `merchant = 97.00`;
- Refund: `tax = 19.00 × 59.50 / 119.00 = 9.50`, `net = 50.00`, `fee = 3.00 × 50.00 / 100.00 = 1.50`, `merchant = 48.50`.

`ledger_accounts`:

| id | type             | currency | merchant_id | country_code | balance |
|----|------------------|----------|-------------|--------------|---------|
| 1  | CASH_CLEARING    | EUR      |             |              |         |
| 2  | TAX_PAYABLE      | EUR      |             | DE           |         |
| 3  | FEE_REVENUE      | EUR      |             |              |         |
| 4  | MERCHANT_PAYABLE | EUR      | MERCH-001   |              | -48.50  |

`ledger_transactions`:

| id | type             | source_type | source_id      | effective_at         |
|----|------------------|-------------|----------------|----------------------|
| 1  | PAYMENT_SETTLED  | payment     | PAY-0123456789 | 2026-10-01T10:00:00Z |
| 2  | PAYMENT_REFUNDED | refund      | REF-0123456789 | 2026-10-02T09:00:00Z |

`ledger_entries`:

| id | transaction_id | account_id             | amount  |
|----|----------------|------------------------|---------|
| 1  | 1              | 1 (Cash Clearing)      | 119.00  |
| 2  | 1              | 2 (Tax Payable DE)     | -19.00  |
| 3  | 1              | 3 (Fee Revenue)        | -3.00   |
| 4  | 1              | 4 (Merchant MERCH-001) | -97.00  |
| 5  | 2              | 1 (Cash Clearing)      | -59.50  |
| 6  | 2              | 2 (Tax Payable DE)     | 9.50    |
| 7  | 2              | 3 (Fee Revenue)        | 1.50    |
| 8  | 2              | 4 (Merchant MERCH-001) | 48.50   |

Resulting balances: the MoR holds `59.50 EUR`, owes `9.50 EUR` to the German tax authority
and `48.50 EUR` to `MERCH-001`, and earned `1.50 EUR` in fees.

##### Reporting queries

A merchant's balances, one per currency, read straight from the cached balance:

```sql
SELECT currency, -balance AS balance
FROM ledger_accounts
WHERE type = 'MERCHANT_PAYABLE'
  AND merchant_id = 'MERCH-001'
ORDER BY currency;
```

The MoR tax statement for a period, per country and currency:

```sql
SELECT a.country_code, a.currency, -SUM(e.amount) AS tax_payable
FROM ledger_entries e
         JOIN ledger_accounts a ON a.id = e.account_id
         JOIN ledger_transactions t ON t.id = e.transaction_id
WHERE a.type = 'TAX_PAYABLE'
  AND t.effective_at >= '2026-10-01T00:00:00Z'
  AND t.effective_at < '2026-11-01T00:00:00Z'
GROUP BY a.country_code, a.currency
ORDER BY a.country_code, a.currency;
```

For the example above, the first query returns `EUR 48.50` and the second returns `DE EUR 9.50`.

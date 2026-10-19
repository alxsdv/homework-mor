# Manual API testing

Base URL: `http://localhost:8080` · Swagger UI: http://localhost:8080/swagger-ui.html · Grafana: http://localhost:3000

## Start / stop

The `app` image only packages a jar that is already built, so build it first:

```bash
./mvnw clean install                 # build backend/target/homework-backend-*.jar
docker compose up -d --build         # start Postgres, app and Grafana (rebuilds the app image)
docker compose logs -f app           # follow app logs; ready when "Started Application" appears
docker compose ps                    # check all three containers are up: postgres, app, lgtm
```

```bash
docker compose down                  # stop, keep the database
docker compose down -v               # stop and wipe the database
```

Stored traces and logs live inside the `lgtm` container, so any `docker compose down` (with or without `-v`) wipes them.

The expected values below assume an **empty database**: run `docker compose down -v` before starting,
and send the requests **in order**, because each one builds on the ones before it.
On a fresh database only the merchants exist, seeded by migrations:

| Merchant  | Fee rate |
|-----------|----------|
| MERCH-001 | 3%       |
| MERCH-002 | 5%       |
| MERCH-003 | 2.5%     |

Every request with a body needs `Content-Type: application/json`.
Errors come back as `application/problem+json` with `title`, `status`, `detail` and `instance`.
Generated IDs (`PAY-…`, `REF-…`) differ on every run.

---

## 1. `POST /payments`: record a settled payment

How the amounts are calculated: `net = total / (1 + taxRate)`, `tax = total − net`, `fee = net × merchant fee rate`,
`merchantAmount = net − fee`. The response shows `settledAt` in UTC and `taxRate` with trailing zeros dropped
(`"0.10"` → `"0.1"`). `taxCountry` comes from the customer's billing country.

### ✅ 1a. MERCH-001 (3% fee): 119.00 EUR, 19% tax

`POST http://localhost:8080/payments`

```json
{
  "externalId": "EXT-TEST-001",
  "settledAt": "2026-10-01T12:00:00+02:00",
  "merchantId": "MERCH-001",
  "customer": {
    "firstName": "Erika",
    "lastName": "Mustermann",
    "email": "erika@example.com",
    "billingContact": {
      "addressLine1": "Hauptstraße 1",
      "addressLine2": "Apartment 4",
      "countryCode": "DE",
      "postalCode": "10115"
    }
  },
  "currency": "EUR",
  "totalAmount": "119.00",
  "taxRate": "0.19"
}
```

Expect **201 Created**:

```json
{
  "paymentId": "PAY-G0Z8XJBDBZ",
  "externalId": "EXT-TEST-001",
  "merchantId": "MERCH-001",
  "settledAt": "2026-10-01T10:00:00Z",
  "currency": "EUR",
  "totalAmount": "119.00",
  "taxRate": "0.19",
  "taxAmount": "19.00",
  "netAmount": "100.00",
  "feeAmount": "3.00",
  "merchantAmount": "97.00",
  "taxCountry": "DE",
  "customer": {
    "firstName": "Erika",
    "lastName": "Mustermann",
    "email": "erika@example.com",
    "billingContact": {
      "addressLine1": "Hauptstraße 1",
      "addressLine2": "Apartment 4",
      "countryCode": "DE",
      "postalCode": "10115"
    }
  }
}
```

**Copy the `paymentId`**: section 2 needs it.

Idempotency check: send the exact same request again. You get **201** with the **same** `paymentId`, and no
second payment is recorded (balances in section 3 don't double).

### ✅ 1b. MERCH-002 (5% fee): 275.00 EUR, 10% tax

`POST http://localhost:8080/payments`

```json
{
  "externalId": "EXT-TEST-002",
  "settledAt": "2026-10-03T15:30:00+02:00",
  "merchantId": "MERCH-002",
  "customer": {
    "firstName": "Louis",
    "lastName": "Martin",
    "email": "louis.martin@example.com",
    "billingContact": {
      "addressLine1": "8 Rue de Rivoli",
      "countryCode": "FR",
      "postalCode": "75001"
    }
  },
  "currency": "EUR",
  "totalAmount": "275.00",
  "taxRate": "0.10"
}
```

Expect **201 Created**:

```json
{
  "paymentId": "PAY-9DSG0E5DP5",
  "externalId": "EXT-TEST-002",
  "merchantId": "MERCH-002",
  "settledAt": "2026-10-03T13:30:00Z",
  "currency": "EUR",
  "totalAmount": "275.00",
  "taxRate": "0.1",
  "taxAmount": "25.00",
  "netAmount": "250.00",
  "feeAmount": "12.50",
  "merchantAmount": "237.50",
  "taxCountry": "FR",
  "customer": {
    "firstName": "Louis",
    "lastName": "Martin",
    "email": "louis.martin@example.com",
    "billingContact": {
      "addressLine1": "8 Rue de Rivoli",
      "addressLine2": null,
      "countryCode": "FR",
      "postalCode": "75001"
    }
  }
}
```

### ✅ 1c. MERCH-003 (2.5% fee): 96.00 GBP, 20% tax

`POST http://localhost:8080/payments`

```json
{
  "externalId": "EXT-TEST-003",
  "settledAt": "2026-10-05T09:15:00+01:00",
  "merchantId": "MERCH-003",
  "customer": {
    "firstName": "Emily",
    "lastName": "Clarke",
    "email": "emily.clarke@example.com",
    "billingContact": {
      "addressLine1": "221B Baker Street",
      "countryCode": "GB",
      "postalCode": "NW1 6XE"
    }
  },
  "currency": "GBP",
  "totalAmount": "96.00",
  "taxRate": "0.20"
}
```

Expect **201 Created**:

```json
{
  "paymentId": "PAY-20N7NZ8AKC",
  "externalId": "EXT-TEST-003",
  "merchantId": "MERCH-003",
  "settledAt": "2026-10-05T08:15:00Z",
  "currency": "GBP",
  "totalAmount": "96.00",
  "taxRate": "0.2",
  "taxAmount": "16.00",
  "netAmount": "80.00",
  "feeAmount": "2.00",
  "merchantAmount": "78.00",
  "taxCountry": "GB",
  "customer": {
    "firstName": "Emily",
    "lastName": "Clarke",
    "email": "emily.clarke@example.com",
    "billingContact": {
      "addressLine1": "221B Baker Street",
      "addressLine2": null,
      "countryCode": "GB",
      "postalCode": "NW1 6XE"
    }
  }
}
```

### ✅ 1d. MERCH-001 again, in another currency: 107.00 USD, 7% tax

MERCH-001 now has payments in EUR (1a) and USD. Balances are kept per currency and never converted, so
MERCH-001's balance gets a second line (see 3a). The US customer also adds a US row to the tax statement (4a).

`POST http://localhost:8080/payments`

```json
{
  "externalId": "EXT-TEST-004",
  "settledAt": "2026-10-06T10:00:00-07:00",
  "merchantId": "MERCH-001",
  "customer": {
    "firstName": "John",
    "lastName": "Smith",
    "email": "john.smith@example.com",
    "billingContact": {
      "addressLine1": "500 Market Street",
      "addressLine2": "Suite 200",
      "countryCode": "US",
      "postalCode": "94105"
    }
  },
  "currency": "USD",
  "totalAmount": "107.00",
  "taxRate": "0.07"
}
```

Expect **201 Created**:

```json
{
  "paymentId": "PAY-BDQ3578PSY",
  "externalId": "EXT-TEST-004",
  "merchantId": "MERCH-001",
  "settledAt": "2026-10-06T17:00:00Z",
  "currency": "USD",
  "totalAmount": "107.00",
  "taxRate": "0.07",
  "taxAmount": "7.00",
  "netAmount": "100.00",
  "feeAmount": "3.00",
  "merchantAmount": "97.00",
  "taxCountry": "US",
  "customer": {
    "firstName": "John",
    "lastName": "Smith",
    "email": "john.smith@example.com",
    "billingContact": {
      "addressLine1": "500 Market Street",
      "addressLine2": "Suite 200",
      "countryCode": "US",
      "postalCode": "94105"
    }
  }
}
```

### ✅ 1e. MERCH-002 again, same currency, another country: 238.00 EUR, 19% tax, German customer

MERCH-002 now has EUR payments from France (1b) and Germany. Its balance stays a single EUR line that adds up both
payments (see 3a). Tax is owed to the customer's country, so the German tax goes to the DE row of the tax
statement, together with MERCH-001's German tax from 1a (see 4a).

`POST http://localhost:8080/payments`

```json
{
  "externalId": "EXT-TEST-005",
  "settledAt": "2026-10-07T18:45:00+02:00",
  "merchantId": "MERCH-002",
  "customer": {
    "firstName": "Max",
    "lastName": "Mustermann",
    "email": "max@example.com",
    "billingContact": {
      "addressLine1": "Marienplatz 8",
      "countryCode": "DE",
      "postalCode": "80331"
    }
  },
  "currency": "EUR",
  "totalAmount": "238.00",
  "taxRate": "0.19"
}
```

Expect **201 Created**:

```json
{
  "paymentId": "PAY-JK30VZ24H6",
  "externalId": "EXT-TEST-005",
  "merchantId": "MERCH-002",
  "settledAt": "2026-10-07T16:45:00Z",
  "currency": "EUR",
  "totalAmount": "238.00",
  "taxRate": "0.19",
  "taxAmount": "38.00",
  "netAmount": "200.00",
  "feeAmount": "10.00",
  "merchantAmount": "190.00",
  "taxCountry": "DE",
  "customer": {
    "firstName": "Max",
    "lastName": "Mustermann",
    "email": "max@example.com",
    "billingContact": {
      "addressLine1": "Marienplatz 8",
      "addressLine2": null,
      "countryCode": "DE",
      "postalCode": "80331"
    }
  }
}
```

### ❌ 1f. Reused `externalId` with a different amount

Same as 1a, but `totalAmount` is `150.00` instead of `119.00`.

`POST http://localhost:8080/payments`

```json
{
  "externalId": "EXT-TEST-001",
  "settledAt": "2026-10-01T12:00:00+02:00",
  "merchantId": "MERCH-001",
  "customer": {
    "firstName": "Erika",
    "lastName": "Mustermann",
    "email": "erika@example.com",
    "billingContact": {
      "addressLine1": "Hauptstraße 1",
      "addressLine2": "Apartment 4",
      "countryCode": "DE",
      "postalCode": "10115"
    }
  },
  "currency": "EUR",
  "totalAmount": "150.00",
  "taxRate": "0.19"
}
```

Expect **409 Conflict**:

```json
{
  "detail": "Payment with external ID EXT-TEST-001 for merchant MERCH-001 already exists with a different amount, currency or settlement time",
  "instance": "/payments",
  "status": 409,
  "title": "Conflict"
}
```

---

## 2. `POST /payments/{paymentId}/refund`: refund a payment, fully or partly

Replace `{paymentId}` with the `paymentId` from **1a**. Leaving out `refundAmount` refunds whatever is still
refundable. A refund splits into tax, fee and merchant amounts in the same proportions as its payment.

### ✅ 2a. Partial refund: 59.50 of the 119.00

`POST http://localhost:8080/payments/{paymentId}/refund`

```json
{
  "externalId": "EXT-TEST-REFUND-001",
  "refundedAt": "2026-10-02T09:00:00+02:00",
  "refundAmount": "59.50"
}
```

Expect **201 Created**:

```json
{
  "refundId": "REF-PPMADW7VJE",
  "externalId": "EXT-TEST-REFUND-001",
  "paymentId": "PAY-G0Z8XJBDBZ",
  "merchantId": "MERCH-001",
  "refundedAt": "2026-10-02T07:00:00Z",
  "currency": "EUR",
  "refundAmount": "59.50",
  "taxRate": "0.19",
  "taxAmount": "9.50",
  "netAmount": "50.00",
  "feeAmount": "1.50",
  "merchantAmount": "48.50",
  "taxCountry": "DE"
}
```

As with payments, sending the same request again returns the same `refundId`.

### ❌ 2b. Refund more than is left

After 2a, 59.50 is left to refund.

`POST http://localhost:8080/payments/{paymentId}/refund`

```json
{
  "externalId": "EXT-TEST-REFUND-002",
  "refundedAt": "2026-10-02T10:00:00+02:00",
  "refundAmount": "100.00"
}
```

Expect **409 Conflict**:

```json
{
  "detail": "Refund amount 100.00 exceeds the refundable amount 59.50 of payment PAY-G0Z8XJBDBZ",
  "instance": "/payments/PAY-G0Z8XJBDBZ/refund",
  "status": 409,
  "title": "Conflict"
}
```

---

## 3. `GET /merchants/{merchantId}/balances`: what we owe the merchant, per currency

### ✅ 3a. Balances after sections 1 and 2

`GET http://localhost:8080/merchants/MERCH-001/balances`

Expect **200 OK** with one line per currency, because MERCH-001 was paid in EUR (1a) and USD (1d):

- EUR 48.50: 97.00 from the 1a payment, minus 48.50 from the 2a refund.
- USD 97.00: from the 1d payment.

```json
{
  "merchantId": "MERCH-001",
  "balances": [
    { "currency": "EUR", "payable": "48.50" },
    { "currency": "USD", "payable": "97.00" }
  ]
}
```

The other two merchants:

| Request                                                 | `balances`                                     | Why                                                    |
|---------------------------------------------------------|------------------------------------------------|--------------------------------------------------------|
| `GET http://localhost:8080/merchants/MERCH-002/balances` | `[{ "currency": "EUR", "payable": "427.50" }]` | 237.50 (1b, FR) + 190.00 (1e, DE): one currency, one line |
| `GET http://localhost:8080/merchants/MERCH-003/balances` | `[{ "currency": "GBP", "payable": "78.00" }]`  | 1c                                                     |

On a fresh database, before section 1, every merchant returns `"balances": []`.

### ❌ 3b. Unknown merchant

`GET http://localhost:8080/merchants/MERCH-999/balances`

Expect **404 Not Found**:

```json
{
  "detail": "Unknown merchant: MERCH-999",
  "instance": "/merchants/MERCH-999/balances",
  "status": 404,
  "title": "Not Found"
}
```

---

## 4. `GET /tax-statement`: tax owed per country and currency for a period

Query parameters: `from` (inclusive) and `to` (exclusive) are ISO dates, both required. `zone` is optional
(a time zone ID like `Europe/Berlin` or an offset; defaults to `UTC`). To send an offset like `+02:00`,
encode `+` as `%2B`.

### ✅ 4a. October 2026, Berlin time

`GET http://localhost:8080/tax-statement?from=2026-10-01&to=2026-11-01&zone=Europe/Berlin`

Expect **200 OK** with one row per country and currency. The rows don't depend on merchants:

| Row       | Payable | Made up of                                                                    |
|-----------|---------|-------------------------------------------------------------------------------|
| DE / EUR  | 47.50   | MERCH-001: 19.00 (1a) − 9.50 (2a refund); MERCH-002: 38.00 (1e)               |
| FR / EUR  | 25.00   | MERCH-002: 25.00 (1b)                                                          |
| GB / GBP  | 16.00   | MERCH-003: 16.00 (1c)                                                          |
| US / USD  | 7.00    | MERCH-001: 7.00 (1d)                                                           |

MERCH-002's EUR payments are split into the FR and DE rows here, although its balance (3a) is one EUR line.

```json
{
  "from": "2026-10-01",
  "to": "2026-11-01",
  "zone": "Europe/Berlin",
  "statements": [
    { "countryCode": "DE", "currency": "EUR", "payable": "47.50" },
    { "countryCode": "FR", "currency": "EUR", "payable": "25.00" },
    { "countryCode": "GB", "currency": "GBP", "payable": "16.00" },
    { "countryCode": "US", "currency": "USD", "payable": "7.00" }
  ]
}
```

### ❌ 4b. `from` after `to`

`GET http://localhost:8080/tax-statement?from=2026-11-01&to=2026-10-01`

Expect **400 Bad Request**:

```json
{
  "detail": "from (2026-11-01) must be before to (2026-10-01); to is exclusive",
  "instance": "/tax-statement",
  "status": 400,
  "title": "Bad Request"
}
```

---

## 5. Tracing: follow a request in Grafana

Every request to the containerized app is traced by the OpenTelemetry Java agent. Grafana at http://localhost:3000
shows the traces (Tempo) and the app's logs (Loki). No login is needed.

Send **1a** and **2a** first. A new trace can take up to half a minute to show up in search; opening it by its ID
(5d) works right away.

### ✅ 5a. Find the trace of a request

Grafana → **Explore** → data source **Tempo** → **Search**, Service Name `homework`, Span Name `POST /payments`,
then **Run query**. The app's startup (Flyway migrations) and the connection pool's periodic checks (span name `app`) also leave
traces, which is why filtering by span name helps. In the **TraceQL** tab the same search is `{name="POST /payments"}`.

### ✅ 5b. Read the trace of 1a

Open the trace. Each bar is one step with its duration, nested under the step that called it:

```
POST /payments                                            HTTP request
└─ PaymentsController.recordSettledPayment
   └─ RecordSettledPaymentUseCaseImpl.recordSettledPayment
      └─ SpringTransactionRunner.inTransaction            one transaction attempt
         ├─ PaymentsRepositoryImpl.findByMerchantAndExternalId
         │  └─ SELECT "public"."payments"                 SQL statement
         ├─ MerchantLookupAdapter.findById → FindMerchantUseCaseImpl.findById → MerchantsRepositoryImpl.findById
         ├─ PaymentsRepositoryImpl.nextId
         ├─ PaymentsRepositoryImpl.addPayment
         │  └─ INSERT "public"."payments"
         ├─ LedgerSettlementAdapter.recordSettlement
         │  └─ PostToLedgerUseCaseImpl.post
         │     └─ SpringTransactionRunner.inTransaction   joins the outer transaction, see below
         │        └─ LedgerRepositoryImpl.* with their SQL
         ├─ COMMIT                                        the transaction's commit
         └─ COMMIT                                        a few µs, see below
```

- Click a SQL span to see its statement (`db.statement`) and its actual values (`db.query.parameter.0`, `.1`, …):
  the `SELECT` on `payments` has `MERCH-001` and `EXT-TEST-001`. The agent names a SQL span after its operation
  and table; when it can't tell the table it uses the database name instead (`SELECT app`, or just `app` for the
  connection check at the start of the transaction). The statement itself is always in `db.statement`.
- The **second, nested `SpringTransactionRunner.inTransaction`** under `PostToLedgerUseCaseImpl.post` is not a new
  transaction: the ledger joins the payment's transaction. A **retry** would look different: a second
  `inTransaction` directly under the use case, next to the first.
- The transaction ends with a `COMMIT` (or a `ROLLBACK` on error), followed by a second `COMMIT` span of a few
  microseconds. That one is bookkeeping in the JDBC layer, not a second transaction.
- A failed request is traced too. Send **1f** and open its trace: the use case span is red, and its event names
  `PaymentConflictException`.

### ✅ 5c. See the request's logs

On any span of the trace, click **Logs for this span**. Loki opens with all of the request's log lines
(`Request POST /payments`, the use case lines, `Response POST /payments -> 201 …`), whichever span you clicked,
and no lines from other requests.

### ✅ 5d. From a console line to its trace

Every request line in the console carries its trace ID in brackets (lines outside a request show `[]`):

```bash
docker compose logs app | grep 'Request POST /payments'
```

```
… [nio-8080-exec-1] [9cf694c5dd539def5f3c33a7c66938d6] o.e.shared.web.RequestLoggingFilter : Request POST /payments
```

Paste the ID into the **TraceQL** tab of Tempo (or into Explore → Tempo → **Query type: TraceQL**) to open the same trace.

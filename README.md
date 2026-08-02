# CoreLedger

A mini core-banking backend that moves money between accounts correctly —
even when requests are retried and even when many transfers race each other
at once. Built to demonstrate the parts of backend engineering that generic
CRUD projects skip: transactional integrity, idempotency, and concurrency
safety, which are exactly the concerns a real bank's transaction system has
to get right.

## The problem

Any system that moves money has three hard requirements that are easy to
get wrong:

1. **A retried request must not move money twice.** Networks fail. If a
   client sends a transfer, times out, and retries, the second request must
   not double-charge the account.
2. **Two simultaneous transfers touching the same account must not
   corrupt the balance.** If two withdrawals both read a balance of ₹1000
   at the same instant and both proceed, the account can be overdrawn even
   though each check individually looked safe — a *lost update*.
3. **The books must always balance and be auditable.** A raw `balance`
   column that gets overwritten in place gives you no trail to prove where
   money went if a customer disputes a transaction.

CoreLedger solves all three.

## How it solves them

| Problem | Solution |
|---|---|
| Duplicate retries | Every transfer request carries a client-supplied `idempotencyKey`, stored as a DB-level unique constraint on the `transactions` table. A repeat request returns the original result instead of moving money again. |
| Lost updates under concurrency | `SELECT ... FOR UPDATE` row locks on both accounts involved in a transfer, acquired in a **fixed order** (lower account ID first) regardless of transfer direction — this is what prevents deadlock when A→B and B→A happen at the same time. |
| Auditability | Double-entry bookkeeping: every transfer writes exactly one DEBIT and one CREDIT ledger row. Summing an account's ledger entries independently reconstructs its balance — this is the same principle real accounting systems use. |

The concurrency claim isn't just asserted — `TransferServiceTest` fires 50
concurrent transfer requests from a 10-thread pool at the same source
account and asserts that total money in the system is exactly conserved
afterward, which would fail immediately without the locking strategy above.

## Tech stack

Java 17, Spring Boot 3, Spring Data JPA, Spring Security (JWT), PostgreSQL,
H2 (tests), Docker Compose, Maven.

## Project structure

```
src/main/java/com/coreledger/
  entity/       Account, Transaction, LedgerEntry, enums
  repository/   Spring Data repos, incl. pessimistic-lock query
  service/      TransferService (core logic), AccountService
  controller/   REST endpoints
  security/     JWT generation + filter
  config/       Spring Security config
  exception/    Domain exceptions + global handler -> clean HTTP error bodies
```

## Running it

```bash
# 1. Start Postgres
docker compose up -d

# 2. Run the app
./mvnw spring-boot:run

# 3. Run the tests (uses in-memory H2, no Docker needed)
./mvnw test
```

## API walkthrough

**Login** (demo credentials — swap for a real Users table in production):
```bash
curl -X POST localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
# => { "token": "eyJ..." }
```

**Create two accounts:**
```bash
curl -X POST localhost:8080/api/accounts \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"ownerName":"Alice","openingBalance":1000.00,"currency":"INR"}'

curl -X POST localhost:8080/api/accounts \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"ownerName":"Bob","openingBalance":500.00,"currency":"INR"}'
```

**Transfer money:**
```bash
curl -X POST localhost:8080/api/transfers \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"idempotencyKey":"txn-001","fromAccountNumber":"<A>","toAccountNumber":"<B>","amount":200.00}'
```

**Retry the exact same request** (same `idempotencyKey`) → returns the same
result instead of transferring again.

**View a statement** (double-entry ledger for an account):
```bash
curl localhost:8080/api/accounts/<accountNumber>/statement -H "Authorization: Bearer <token>"
```

## What to talk about in an interview

- Why `SELECT FOR UPDATE` beats optimistic locking here (transfers are
  short and contention-prone, so blocking briefly is cheaper than retrying
  failed optimistic writes under load) — and where you'd choose the
  opposite tradeoff.
- Why the lock-acquisition order has to be independent of transfer
  direction to avoid deadlock.
- Why idempotency keys belong at the API layer, not just "check if it
  looks similar" — exact key matching is what makes retries provably safe.
- How double-entry entries make the system auditable independent of the
  live `balance` column, which is what a real ledger needs to survive a
  dispute or reconciliation.

## Possible extensions

- Scheduled account statement PDF/email export
- Multi-currency transfers with FX rate service
- Outbox pattern + Kafka for publishing transaction events downstream
- Rate limiting per account on the transfer endpoint

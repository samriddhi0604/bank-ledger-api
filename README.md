# Mini Bank Ledger API

A Spring Boot REST API for managing bank accounts, deposits, withdrawals, and transaction
history. This is a **learning/practice project** built to exercise Spring Boot fundamentals:
layered architecture, request validation, transactional integrity, and consistent error handling.
It is not production banking software.

## Stack

- Java 21 (compiled with `--release 21`)
- Spring Boot 3.3.4: Web, Data JPA, Validation
- H2 in-memory database
- Maven
- JUnit 5 via `spring-boot-starter-test`

## Prerequisites

- JDK 21 or newer (the build targets Java 21 bytecode)
- Maven 3.9+
- Internet access on the first build, to download dependencies from Maven Central

## Run

```bash
mvn spring-boot:run
```

The API starts on <http://localhost:8080>. The H2 console is at <http://localhost:8080/h2-console>
(JDBC URL `jdbc:h2:mem:bankledger`, user `sa`, empty password).

## Test

```bash
mvn test
```

The tests are `@SpringBootTest` + `@Transactional` and run against the real H2 database through
the real service, with no mocks. Each test rolls back afterwards.

## API

| Method | Path                              | Description                    |
|--------|-----------------------------------|--------------------------------|
| POST   | `/api/accounts`                   | Create an account (201)        |
| GET    | `/api/accounts`                   | List all accounts              |
| GET    | `/api/accounts/{id}`              | Get one account (404 if none)  |
| POST   | `/api/accounts/{id}/deposit`      | Deposit `{"amount": ...}`      |
| POST   | `/api/accounts/{id}/withdraw`     | Withdraw `{"amount": ...}`     |
| GET    | `/api/accounts/{id}/transactions` | History, newest first          |

Errors share one JSON shape: `{"timestamp": ..., "status": 400, "error": "message"}`.

## Example curl commands

Happy path:

```bash
# Create an account -> 201
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"ownerName":"Asha","initialBalance":100.00}'

# Deposit -> 200, balance 150.25
curl -X POST http://localhost:8080/api/accounts/1/deposit \
  -H "Content-Type: application/json" -d '{"amount":50.25}'

# Withdraw -> 200, balance 120.25
curl -X POST http://localhost:8080/api/accounts/1/withdraw \
  -H "Content-Type: application/json" -d '{"amount":30}'

# Get one / list all / history
curl http://localhost:8080/api/accounts/1
curl http://localhost:8080/api/accounts
curl http://localhost:8080/api/accounts/1/transactions
```

Failure paths:

```bash
# Withdraw more than the balance -> 400 "Insufficient funds: ..."
curl -X POST http://localhost:8080/api/accounts/1/withdraw \
  -H "Content-Type: application/json" -d '{"amount":1000}'

# Nonexistent account -> 404
curl http://localhost:8080/api/accounts/999
curl http://localhost:8080/api/accounts/999/transactions

# Zero, negative, or missing amount -> 400
curl -X POST http://localhost:8080/api/accounts/1/deposit \
  -H "Content-Type: application/json" -d '{"amount":0}'
curl -X POST http://localhost:8080/api/accounts/1/deposit \
  -H "Content-Type: application/json" -d '{"amount":-5}'
curl -X POST http://localhost:8080/api/accounts/1/deposit \
  -H "Content-Type: application/json" -d '{}'

# Blank owner or negative initial balance -> 400
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" -d '{"ownerName":" ","initialBalance":10}'
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" -d '{"ownerName":"X","initialBalance":-1}'
```

## Design notes

- **Layering.** Controller -> Service -> Repository. Controllers only translate HTTP to service
  calls; all business rules live in `AccountService`. DTOs sit on the API boundary so the JPA
  entities can change without breaking the API contract.
- **Money is `BigDecimal`** (precision 19, scale 2), never `double`/`float`, which cannot represent
  decimal amounts exactly.
- **Enums are stored as `STRING`**, not `ORDINAL`. Reordering the enum later would otherwise
  silently corrupt existing rows.
- **Balance and ledger commit together.** `deposit` and `withdraw` are `@Transactional`: the
  balance update and the `Transaction` record are written in one database transaction. If either
  write fails, both roll back, so the ledger cannot drift from the real balance. Each
  `Transaction` also stores `balanceAfter`, a snapshot for auditing without replaying history.
- **Check-then-update in one transaction.** `withdraw` checks the balance and updates it inside the
  same transaction. Splitting them across transaction boundaries would let two requests both pass
  the check on the same stale balance.
- **What that does and doesn't guarantee.** Keeping the check and update in one transaction is
  necessary, but on its own it does not fully serialize concurrent withdrawals: under the default
  READ_COMMITTED isolation, two transactions can each read the same balance, and the second
  write can overwrite the first (a lost update). Closing that gap needs a row lock
  (`@Lock(PESSIMISTIC_WRITE)` on the account read) or optimistic locking (`@Version` on
  `Account`) with a retry. This project does not implement either yet, and it has not been
  load-tested for it.
- **Consistent errors.** `GlobalExceptionHandler` (`@RestControllerAdvice`) maps
  `AccountNotFoundException` -> 404, `InsufficientFundsException` -> 400, and `@Valid` failures
  -> 400 with the first field error's message. `getTransactionHistory` verifies the account
  exists first, so an unknown account gives 404 rather than an empty list.

## Known limitations

- No authentication or authorization.
- H2 is in-memory: all data is lost on restart.
- Concurrent withdrawals are not protected by a row lock or optimistic locking (see above), and no
  concurrency load testing has been performed.
- List endpoints are not paginated.
- No idempotency keys: a retried deposit or withdrawal is applied twice.
- Test coverage is service-layer only; there are no controller/HTTP-level tests.

# [CLAUDE.md](http://CLAUDE.md)

Guidance for Claude Code when working in this repository.

## Project

**Mini Bank Ledger API** — a Spring Boot REST API for managing bank accounts, deposits,
withdrawals, and transaction history. Built as a focused practice project to genuinely
demonstrate Spring Boot fundamentals (layered architecture, validation, transactional
integrity, proper error handling) for a job application (Kotak Tech).

**Nothing exists yet.** This repository is empty. Build the entire project from scratch,
following the steps below in order. Each step should compile, run, and be verified (via `mvn test` and/or a manual curl check) before moving to the next — don't write the whole thing
speculatively and debug everything at the end.

## Build Order



### Step 0 — Project scaffolding

- Initialize a Maven project: `groupId com.samriddhi`, `artifactId bank-ledger-api`.
- `pom.xml` dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`,
`spring-boot-starter-validation`, `h2` (runtime scope), `spring-boot-starter-test` (test
scope). Use `spring-boot-starter-parent` version 3.3.4 (or the latest 3.3.x) as the parent,
Java 21 as the source/target version.
- `src/main/resources/application.properties`: configure an in-memory H2 datasource
(`jdbc:h2:mem:bankledger`), `spring.jpa.hibernate.ddl-auto=update`, enable the H2 console at
`/h2-console`, server port 8080.
- Create the main application class (`BankLedgerApiApplication`) with `@SpringBootApplication`.
- Initialize git, add a `.gitignore` for `target/`, `.idea/`, `*.iml`.
- **Verify:** run `mvn spring-boot:run` and confirm the app starts with no errors (even with no
endpoints yet) before continuing.
- **Commit:** "Initial Spring Boot project scaffolding with H2 config."



### Step 1 — Domain model

- `model/Account.java`: JPA entity with `id` (auto-generated), `ownerName` (String),
`balance` (BigDecimal, precision 19 scale 2 — never use `double`/`float` for money), and
`createdAt` (Instant, set via `@PrePersist`).
- `model/Transaction.java`: JPA entity with `id`, `accountId` (Long), `type` (enum: `DEPOSIT` /
`WITHDRAWAL`, stored as `@Enumerated(EnumType.STRING)` — never `ORDINAL`, since reordering the
enum later would silently corrupt existing data), `amount` (BigDecimal), `balanceAfter`
(BigDecimal — a snapshot of the balance right after this transaction, useful for auditing
without recomputing from history), and `timestamp` (Instant).
- **Verify:** the app still starts cleanly and Hibernate creates both tables (check the startup
logs or the H2 console at `/h2-console`).
- **Commit:** "Add Account and Transaction JPA entities."



### Step 2 — Repositories

- `repository/AccountRepository.java`: `extends JpaRepository<Account, Long>` — no custom
methods needed yet.
- `repository/TransactionRepository.java`: `extends JpaRepository<Transaction, Long>`, plus a
derived query method `findByAccountIdOrderByTimestampDesc(Long accountId)` for transaction
history in reverse-chronological order.
- **Commit:** "Add Account and Transaction repositories."



### Step 3 — Domain exceptions and global error handling

- `exception/AccountNotFoundException.java` and `exception/InsufficientFundsException.java`,
both extending `RuntimeException`.
- `exception/GlobalExceptionHandler.java`: annotated `@RestControllerAdvice`. Handle
`AccountNotFoundException` → HTTP 404, `InsufficientFundsException` → HTTP 400, and
`MethodArgumentNotValidException` (from `@Valid` failures) → HTTP 400 with the first field
error's message. Return a consistent JSON body: `timestamp`, `status`, `error`.
- Writing this before the service/controller layers means every subsequent piece can throw these
exceptions and trust they'll be converted into sane HTTP responses, rather than bolting error
handling on at the end.
- **Commit:** "Add domain exceptions and global exception handler."



### Step 4 — Request DTOs

- `dto/CreateAccountRequest.java`: `ownerName` (`@NotBlank`), `initialBalance` (`@NotNull`,
`@DecimalMin("0.0")`).
- `dto/TransactionRequest.java`: `amount` (`@NotNull`, `@DecimalMin("0.01")` — must reject zero
and negative amounts, not just missing ones).
- Use DTOs on the API boundary rather than accepting/returning JPA entities directly — keeps the
persistence model free to change independently of the API contract.
- **Commit:** "Add validated request DTOs for account creation and transactions."



### Step 5 — Service layer (the core logic — get this right)

- `service/AccountService.java`, constructor-injected with both repositories.
- `createAccount(ownerName, initialBalance)`: builds and saves a new `Account`.
- `getAccount(accountId)`: fetches by id, throws `AccountNotFoundException` if absent — every
other method that needs an account should go through this one rather than duplicating the
fetch-or-throw logic.
- `getAllAccounts()`: returns all accounts.
- `deposit(accountId, amount)`, annotated `@Transactional`: fetch the account, add the amount to
its balance, save it, then save a `Transaction` record with `type=DEPOSIT` and the resulting
balance. The balance update and the transaction-log write must happen in the same transaction
— if either fails, both should roll back, so the ledger can never drift out of sync with the
actual balance.
- `withdraw(accountId, amount)`, also `@Transactional`: fetch the account, check
`balance.compareTo(amount) < 0` and throw `InsufficientFundsException` if so, **inside the
same transaction as the subsequent balance update** — checking funds and updating the balance
must not be separated across transaction boundaries, or two concurrent withdrawals could both
pass the check before either commits, overdrawing the account. Then subtract, save, and log a
`WITHDRAWAL` transaction the same way deposit does.
- `getTransactionHistory(accountId)`: call `getAccount(accountId)` first (so a nonexistent
account returns 404, not a silently empty list), then return the ordered transaction list.
- **Verify:** write and run the tests in Step 7 against this service before building the
controller — a bug here is much easier to catch via a direct service-layer test than by poking
at HTTP endpoints.
- **Commit:** "Add AccountService with deposit/withdraw business logic."



### Step 6 — REST controller

- `controller/AccountController.java`, `@RestController` at `/api/accounts`, constructor-injected
with `AccountService`.
- `POST /api/accounts` → `createAccount`, returns 201 with the created account.
- `GET /api/accounts` → list all.
- `GET /api/accounts/{id}` → single account (404 via the exception handler if missing).
- `POST /api/accounts/{id}/deposit` and `POST /api/accounts/{id}/withdraw`, both taking a
`@Valid @RequestBody TransactionRequest`.
- `GET /api/accounts/{id}/transactions` → transaction history.
- **Verify manually with curl** (see the exact commands template in Step 9's README) —
specifically test the two failure paths: withdrawing more than the balance (expect 400) and
fetching a nonexistent account (expect 404). Getting a controller to return 200 on the happy
path is easy; confirming it returns the *correct error* on the unhappy paths is the part worth
actually checking.
- **Commit:** "Add AccountController with account and transaction endpoints."



### Step 7 — Tests

- `AccountServiceTest.java`, annotated `@SpringBootTest` and `@Transactional` (so each test's DB
changes roll back afterward and tests don't interfere with each other) — test against the real
H2 database via the real service, not mocks, so a passing suite is genuine evidence the logic
works end-to-end.
- Minimum test cases: creating an account sets the initial balance correctly; deposit increases
balance; withdraw decreases balance; withdrawing more than the balance throws
`InsufficientFundsException`; fetching a nonexistent account throws `AccountNotFoundException`;
transaction history correctly records both a deposit and a withdrawal.
- **Run** `mvn test` **and confirm all tests actually pass** — don't consider this step done based on
the code compiling.
- **Commit:** "Add service-layer tests covering deposit, withdrawal, and error cases."



### Step 8 — Documentation

- `README.md`: what the project is, the stack, prerequisites, how to run it (`mvn spring-boot:run`), how to test it (`mvn test`), a full set of example curl commands covering
both success and failure paths, a short "Design notes" section explaining the transactional
and race-condition reasoning from Step 5 (this is worth being able to explain fluently in an
interview, not just having written down), and an honest "Known Limitations" section — no auth,
H2 resets on restart, no concurrency load-testing performed.
- **Commit:** "Add README with setup, usage, and design notes."



## Target Project Structure

```
bank-ledger-api/
  pom.xml
  src/main/java/com/samriddhi/bankledger/
    BankLedgerApiApplication.java
    controller/AccountController.java
    service/AccountService.java
    repository/AccountRepository.java
    repository/TransactionRepository.java
    model/Account.java
    model/Transaction.java
    dto/CreateAccountRequest.java
    dto/TransactionRequest.java
    exception/AccountNotFoundException.java
    exception/InsufficientFundsException.java
    exception/GlobalExceptionHandler.java
  src/main/resources/application.properties
  src/test/java/com/samriddhi/bankledger/AccountServiceTest.java
  README.md
  .gitignore
```



## Commands

```bash
mvn spring-boot:run     # run locally on :8080
mvn test                # run the real test suite (hits an actual H2 DB, not mocks)
```



## Verification Note

Maven Central is unreachable from some sandboxed build environments — if `mvn` fails to resolve
dependencies, that's a network/environment issue, not a code issue; this project should be built
and verified in an environment with normal internet access (a local machine, most CI runners).
Never mark a step "done" based on the code looking correct — actually run it.

## Extending This Project (once the base build above is complete and verified)

Suggested next steps, roughly in order of value for a Kotak Tech-style application (backend
APIs, microservices, DevOps practices) — do not start these until Steps 0-8 above are fully
built and verified:

1. **Swap H2 for Postgres/MySQL** via Docker Compose.
2. **Containerize with a Dockerfile** (multi-stage: Maven build stage, then a slim JRE runtime
  image) — actually build and run the image locally to verify it starts correctly.
3. **Add pagination** to the list endpoints via Spring Data's `Pageable`.
4. **Add Spring Security** (basic auth or JWT) — the base version has zero authentication.
5. **Add a CI pipeline** (GitHub Actions) running `mvn test` on every push.
6. **Add idempotency handling** for deposit/withdraw (client-supplied idempotency key) to guard
  against a retried request double-applying a transaction.



## Git Commit Practice

Commit after each step above, in order, with a message describing what that step added. Do not
backdate commits or manipulate commit timestamps to simulate a longer development history than
what actually happened — a real, same-session commit history is fine and expected.

## Honesty Note for Resume/Interview Use

- This is a **learning/practice project**, not production banking-system experience — describe
it that way if asked.
- Any claim beyond what's actually built and verified here (e.g. "containerized," "deployed,"
"CI pipeline") must come from actually completing and verifying the relevant extension step
above first — build it, run it, confirm it works, *then* update the resume, never the reverse.
- The limitations listed in the README (no auth, H2 resets on restart, no concurrency
load-testing) are real — a clear-eyed answer about what's *not* done is a better interview
signal than implying otherwise.


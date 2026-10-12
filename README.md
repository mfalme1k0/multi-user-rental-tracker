# Multi-user Rental Tracker

A CLI application for a shared rental library where multiple users can register, list their own items for rent, browse other users' listings, rent items, and track returns. Data lives in SQLite and survives between runs.

Built with Java 21+, Maven, and a five-layer architecture (transport / service / repository / domain / infrastructure) enforced by ArchUnit. The multi-user upgrade adds password hashing, authentication, ownership enforcement, centralized validation, structured logging, and Docker support.

**Start here:** `docs/architecture.md` (layers, state machine, design decisions), then `CONTRIBUTING.md` (branches, commits, PRs).

## Features

| **Area** | **What you can do** |
| --- | --- |
| **Authentication** | Register with a unique username and password · log in with existing credentials · log out on exit · passwords stored as hashes, never plaintext |
| **Items** | List a new item (name, description, cost/day) · view your inventory with pagination · open item details · delist / relist your own items |
| **Rentals** | Browse other users' available listings · open item details · rent an item by specifying the number of days · confirm returns as the item owner · view active rentals with pagination |
| **Users and ownership** | Multiple registered users · items automatically associated with their owner · rentals automatically associated with the logged-in renter · ownership enforced in the service layer |
| **Validation** | Centralized validation for User, Item, and Rental fields · reject blank required fields, negative costs, invalid rental durations, missing items, and attempts to rent your own items |
| **State machine** | `AVAILABLE ↔ RENTED`, `AVAILABLE ↔ UNLISTED`, `RENTED → UNLISTED` — delisted-while-out items stay unlisted after return; relisting is blocked while a rental is active |
| **Persistence** | SQLite with foreign keys, CHECK constraints, partial unique index (one active rental per item), and data retained between application runs |
| **Error handling** | Recoverable errors produce readable messages and return control to the menu · unrecoverable startup failures are logged before the application exits |
| **Logging** | Structured log entries with timestamps, severity levels, event names, and context fields · exceptions, validation failures, rental events, and resource lifecycle events recorded without logging passwords |
| **Containerization** | Docker image for building and running the application · mounted volume for SQLite data persistence across container restarts |

## Requirements

- JDK 21+
- Maven 3.9+
- Docker (for containerized execution)

## Build, test, run

Run the test suite and generate the coverage report:

```bash
mvn test
```

Open `target/site/jacoco/index.html` to inspect coverage.

Run the coverage gate:

```bash
mvn verify -Pcoverage-gate
```

The coverage gate checks the configured 100% line and branch coverage requirement.

Run the CLI locally:

```bash
mvn compile exec:java
```

Build the Docker image:

```bash
docker build -t multi-user-rental-tracker .
```

Run the container with persistent SQLite storage:

```bash
docker run -it --name rental-tracker \
  -v rental-tracker-data:/data \
  multi-user-rental-tracker
```

The application must be configured to store its SQLite database at a location inside `/data` for the mounted volume to preserve it. The Docker configuration must use the same database path.

## Project structure

```text
src/main/java/com/rentaltracker/
├── Main.java                       # composition root (constructor injection)
├── domain/                         # records: Item, Rental, User, ItemDetails, RentalDetails
│   ├── ItemStatus.java             # state machine (AVAILABLE, RENTED, UNLISTED)
│   └── RentalStatus.java           # ACTIVE / CLOSED
├── exception/                      # typed unchecked exceptions and exception hierarchy
├── repository/                     # interfaces: ItemRepository, RentalRepository, UserRepository, Transactor
│   └── sqlite/                     # SQLite JDBC implementations + exception translator
├── service/                        # ItemService, RentalService, UserService, Validation
│                                   # authentication, business rules, ownership, validation
├── transport/                      # Transport.java — interactive CLI with login and paginated menus
└── infrastructure/                 # DatabaseManager, connection creation, schema init, configuration
```

```text
src/test/java/com/rentaltracker/
├── architecture/                   # ArchUnit layering test
├── domain/                         # record construction, state transitions, enums
├── exception/                      # exception hierarchy assertions
├── infrastructure/                 # DatabaseManager lifecycle
├── repository/sqlite/              # CRUD integration tests (SQLite)
└── service/                        # business-rule, authentication, validation, ownership, logging tests

docs/
├── architecture.md                 # layers, state machine, design decisions
├── database-design.md              # schema, ER narrative, constraint rationale
└── er-diagram.png                  # entity-relationship diagram

Dockerfile                           # container image build and runtime configuration
```

The structure above describes the intended responsibilities. Additional classes, tests, and configuration files may be introduced as implementation requires.

## Tech stack

| **Component** | **Version / choice** |
| --- | --- |
| Java | 21+ (records, switch expressions, sealed classes) |
| SQLite JDBC | 3.53.2.0 (`org.xerial:sqlite-jdbc`) |
| JUnit Jupiter | 5.11.4 |
| ArchUnit | 1.4.0 |
| JaCoCo | 0.8.15 |
| Maven Compiler Plugin | 3.13.0 |
| Maven Surefire Plugin | 3.5.2 |
| Exec Maven Plugin | 3.5.0 |
| Password hashing | Password4j with bcrypt or Argon2id |
| Structured logging | Shared structured logging implementation |
| Containerization | Docker with a persistent SQLite volume |

Exact dependency versions and logging configuration should be verified against `pom.xml`.

No application framework is required — the project retains its pure Java + JDBC + SQLite architecture.

## Architecture

Five strict layers, with dependency direction enforced by `ArchitectureTest`:

```text
Terminal user
    ↓
Transport         CLI menus, authentication flow, paginated I/O
    ↓
Service           authentication coordination, validation, business rules,
                  ownership enforcement, transaction boundaries
    ↓
Repository        interfaces and SQLite implementations
    ↓
Domain            immutable records, enums, state machine, entity validation
    ↓
Infrastructure    DatabaseManager, schema initialization, connection configuration

Cross-cutting components:
                  password hasher and structured logger

Outside the application layers:
                  Dockerfile and persistent volume configuration
```

The layered structure carries over from the original Rental Tracker. The Service layer enforces business rules independently of the menu, so a user cannot bypass ownership checks by manipulating the available choices.

Nothing above the repository writes SQL or touches `java.sql` — that rule is enforced by a failing architecture test, not just a convention.

The password hasher converts passwords to secure hashes before storage and verifies entered passwords during login. The structured logger records events from every layer using a shared format. Passwords must never appear in logs, whether plaintext or hashed.

Key design decisions remain documented in `docs/architecture.md` (D1–D17). Update the document when the multi-user upgrade introduces or changes architectural decisions.

## Database

Three tables: `users`, `listed_items`, and `rentals`. The schema is initialized automatically on first run.

The `users` table stores a unique username and a hashed password. Listed items are associated with their owners through `owner_id`, and rentals associate items with their renters.

Database-enforced invariants:

- `PRAGMA foreign_keys = ON` on every connection.
- CHECK constraints on `listed_items.status` and `rentals.status`.
- CHECK on `rentals.end_time > rentals.start_time`.
- CHECK on `listed_items.cost_per_day`.
- Partial unique index: at most one active rental per item.
- Foreign keys use `ON DELETE RESTRICT`.
- Unique usernames prevent duplicate accounts.
- Passwords are hashed before being stored; plaintext passwords are never persisted.

Service-layer checks enforce user-specific rules, including preventing users from delisting, relisting, or confirming returns for items they do not own, and preventing users from renting their own items. Database constraints provide an additional layer of integrity.

Full schema and rationale: `docs/database-design.md`.

## Authentication and user flow

On launch, the application asks the user whether to log in or register.

```text
=== Rental tracker ===
Do you have an account?
1) Log in
2) Register
>
```

Registration validates the username and password, hashes the password, and stores the new account. A duplicate username or invalid input produces a readable error.

Login verifies the supplied credentials against the stored account. Invalid credentials do not crash the application or expose internal details.

After successful login, the application identifies the current user automatically:

```text
=== Rental tracker ===
1) List an item
2) View my inventory
3) Browse listings
4) Confirm a return
5) Exit
>
```

Users list items under their own account, browse available listings belonging to other users, and rent items as themselves. Ownership checks are performed by the Service layer, not solely by the menu. Exiting logs the user out and closes the application.

## Validation and error handling

User, Item, and Rental fields are validated centrally at the service boundary before repository operations.

Examples of rejected input include:

- Blank or whitespace-only required fields.
- Negative item costs.
- Rental durations below one day.
- Attempts to rent an item that does not exist.
- Attempts to rent an item owned by the current user.
- Attempts to modify or confirm a return for an item owned by another user.

Users receive short, readable messages rather than database error codes or stack traces. Internal details are recorded in the structured log.

Connections and other resources are cleaned up even when an operation fails. Recoverable failures return control to the menu; unrecoverable startup failures are logged before the application exits.

## Structured logging

Each log entry contains a timestamp, severity level, event name, and relevant context fields.

Example entries:

```text
2026-03-15T02:14:08Z | INFO  | rental.created    | rental_id=42 item_id=7 renter_id=3
2026-03-15T02:14:33Z | WARN  | validation.failed | entity=Item field=cost reason=negative
2026-03-15T02:14:38Z | ERROR | db.query          | op=rental.create error=constraint_violation
```

The application records exceptions, recoverable failures, key rental events, and database connection lifecycle events. Logs must support investigation without exposing passwords or other sensitive authentication details.

## Testing and coverage

The existing test suite remains applicable, including CRUD operations, state transitions, and business rules such as preventing rentals of already-rented or unlisted items.

Additional tests cover:

- **Authentication:** registration stores a hash rather than a plaintext password; correct credentials succeed; incorrect credentials and unknown usernames fail.
- **Validation:** each entity validator accepts valid data and rejects invalid fields with the expected exception.
- **Ownership:** users cannot delist, relist, or confirm returns for another user's items, and cannot rent their own items.
- **Logging:** exceptions generate entries at the expected severity with the expected fields; passwords never appear in log output.
- **Failure handling:** recoverable errors produce appropriate user-facing messages, and resources are cleaned up when operations fail.

Tests should run against real SQLite instances, using isolated, known database state for each test. Assertions should verify the resulting database state rather than relying only on service return values.

Every new service-layer method requires tests, and the existing test suite must continue to pass. Aim for 100% line and branch coverage using JaCoCo, investigate uncovered code, and document any justified gaps.

## Seed data and deliverables

The repository must include:

- The working CLI application and passing tests.
- A JaCoCo coverage report.
- A Dockerfile and persistent SQLite volume configuration.
- A seeded database with at least two users and six listed items distributed across them.
- An ER diagram showing all three tables, their columns, types, constraints, and relationships.
- Git history demonstrating teamwork through reviewed pull requests, small focused commits, and clear commit messages.

## Status

This project extends the original single-user Rental Tracker. The existing layered architecture, domain model, state machine, SQLite persistence, typed exceptions, repositories, transaction support, services, CLI, pagination, and architecture enforcement provide the foundation.

The multi-user requirements add authentication, password hashing, ownership enforcement, centralized entity validation, structured logging, stronger failure handling, Docker support, and additional tests. Completion of these requirements should be tracked against the actual implementation rather than assumed from the original project's status.

## Team

- Project lead: Jackson Macharia
- Team member: Francis Kimani 
- Team member: Fidel Shikokoti

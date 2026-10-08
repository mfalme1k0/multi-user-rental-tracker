# Rental Tracker

A CLI application for renting out your own items (a ladder, a drill…) and tracking who has what.
Data lives in SQLite and survives between runs. Java 21, Maven, five-layer architecture
(transport / service / repository / domain / infrastructure) enforced by ArchUnit.

**Start here:** [docs/architecture.md](docs/architecture.md) (layers, state machine, design decisions),
then [CONTRIBUTING.md](CONTRIBUTING.md) (branches, commits, PRs).

## Features

| Area | What you can do |
|------|-----------------|
| **Items** | List a new item (name, description, cost/day) · view your inventory with pagination · open item details · delist / relist items |
| **Rentals** | Record a rental (pick item → enter renter name + days) · confirm a return · view active rentals with pagination |
| **Users** | First-launch registration · owner identified automatically on subsequent runs · renters created on first rental |
| **State machine** | `AVAILABLE ↔ RENTED`, `AVAILABLE ↔ UNLISTED`, `RENTED → UNLISTED` — delisted-while-out items stay unlisted after return; relist blocked while a rental is active |
| **Persistence** | SQLite with foreign keys, CHECK constraints, partial unique index (one active rental per item) |

## Requirements

- JDK 21+
- Maven 3.9+

## Build, test, run

```bash
mvn test                      # tests + coverage report (target/site/jacoco/index.html)
mvn verify -Pcoverage-gate    # same, and fails below 100 % line/branch coverage
mvn compile exec:java         # run the CLI
```

## Project structure

```
src/main/java/com/rentaltracker/
├── Main.java                       # composition root (constructor injection)
├── domain/                         # records: Item, Rental, User, ItemDetails, RentalDetails
│   ├── ItemStatus.java             # state machine (AVAILABLE, RENTED, UNLISTED)
│   └── RentalStatus.java           # ACTIVE / CLOSED
├── exception/                      # typed unchecked exceptions (see D1–D2 in architecture.md)
├── repository/                     # interfaces: ItemRepository, RentalRepository, UserRepository, Transactor
│   └── sqlite/                     # SQLite JDBC implementations + exception translator
├── service/                        # ItemService, RentalService, UserService, Validation
├── transport/                      # Transport.java — interactive CLI with paginated menus
└── infrastructure/                 # DatabaseManager (connection creation, schema init)

src/test/java/com/rentaltracker/
├── architecture/                   # ArchUnit layering test
├── domain/                         # record construction, state transitions, enums
├── exception/                      # exception hierarchy assertions
├── infrastructure/                 # DatabaseManager lifecycle
├── repository/sqlite/              # CRUD integration tests (in-memory SQLite)
└── service/                        # business-rule tests (real SQLite, @TempDir)

docs/
├── architecture.md                 # single source of truth: layers, state machine, decisions
├── database-design.md              # schema, ER narrative, constraint rationale
└── er-diagram.png                  # entity-relationship diagram
```

## Tech stack

| Component | Version |
|-----------|---------|
| Java | 21 (records, switch expressions, sealed classes) |
| SQLite JDBC | 3.53.2.0 (`org.xerial:sqlite-jdbc`) |
| JUnit Jupiter | 5.11.4 |
| ArchUnit | 1.4.0 |
| JaCoCo | 0.8.15 |
| Maven Compiler Plugin | 3.13.0 |
| Maven Surefire Plugin | 3.5.2 |
| Exec Maven Plugin | 3.5.0 |

No frameworks — pure Java + JDBC + SQLite.

## Architecture

Five strict layers, dependency direction enforced by `ArchitectureTest`:

```
Terminal user
    ↓
Transport         CLI menus + paginated I/O
    ↓
Service           business rules, transaction boundaries
    ↓
Repository        interfaces (+ SQLite implementations)
    ↓
Domain            immutable records, enums, state machine
    ↓
Infrastructure    DatabaseManager, schema init, connection config
```

Nothing above the repository writes SQL or touches `java.sql` — that rule is a failing test, not just a promise.

Key design decisions are documented in [docs/architecture.md](docs/architecture.md) (D1–D17).

## Database

Three tables: `users`, `listed_items`, `rentals`. Schema auto-created on first run.

Database-enforced invariants:
- `PRAGMA foreign_keys = ON` on every connection
- CHECK on `listed_items.status` and `rentals.status`
- CHECK on `rentals.end_time > rentals.start_time`
- CHECK on `listed_items.cost_per_day`
- Partial unique index: at most one active rental per item
- Foreign keys use `ON DELETE RESTRICT`

Full schema and rationale: [docs/database-design.md](docs/database-design.md).

## Status

All layers implemented and wired end-to-end:
domain model, state machine, typed exceptions, SQLite repositories with transaction support,
services (item, rental, user), and interactive CLI with pagination.
Coverage gate and architecture enforcement are in place.

## Team

Francis Kimani (lead), Fidel Shikokoti, Jackson Macharia

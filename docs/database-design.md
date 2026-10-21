# Database Design

**Owner:** Francis

## 1. Overview

Multi-user Rental Tracker uses SQLite as its relational database.

The database schema is defined in:

```text
src/main/resources/schema.sql
```

Database access is handled through JDBC repositories in:

```text
src/main/java/com/rentaltracker/repository/sqlite/
```

Connections are created and configured by `DatabaseManager`.

The database location is supplied to `DatabaseManager` as a `java.nio.file.Path`. The JDBC URL is derived internally from that path using the SQLite JDBC format.

`Main` determines the configured database path and passes it to `DatabaseManager`. CLI argument parsing does not belong in the persistence layer.

SQLite foreign-key enforcement is explicitly enabled for every database connection.

The database contains three tables:

- `users`
- `listed_items`
- `rentals`

The relationships represent users who own listed items, users who rent those items, and the rental history associated with each item.

The database enforces important integrity rules, including unique usernames, valid item and rental statuses, valid rental durations, referential integrity, valid monetary values, and at most one active rental per item.

User authentication is supported through password hashes stored in the `users` table. Plaintext passwords must never be stored in the database.

The application also supports Docker deployment. The SQLite database must be stored at a configurable path that can be backed by a persistent Docker volume.

---

## 2. Entity Relationship Diagram

The database relationships are represented in:

```text
docs/er-diagram.svg
```

The main relationships are:

```text
users
  │
  ├──< listed_items
  │       │
  │       └──< rentals
  │
  └──< rentals
```

A user can own many listed items.

A listed item can have many rental records over its lifetime.

A user can also have many rentals as a renter.

The `users` table participates in two distinct relationships involving rentals:

- As the owner of an item, through `listed_items.owner_id`.
- As the renter of an item, through `rentals.renter_id`.

These relationships use stable numeric user IDs rather than usernames.

---

## 3. `users`

The `users` table stores application accounts and the credentials required for authentication.

| Column | SQLite type | Constraints | Purpose |
|---|---|---|---|
| `id` | `INTEGER` | `PRIMARY KEY` | Database-generated user identifier |
| `username` | `TEXT` | `NOT NULL`, `UNIQUE` | User's unique username |
| `password` | `TEXT` | `NOT NULL` | Hashed password used for authentication |
| `created_at` | `TEXT` | `NOT NULL`, `DEFAULT` | Creation timestamp stored in UTC |

### Schema

```sql
CREATE TABLE IF NOT EXISTS users (
                                     id INTEGER PRIMARY KEY,
                                     username TEXT NOT NULL UNIQUE,
                                     password TEXT NOT NULL,
                                     created_at TEXT NOT NULL
                                     DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
    );
```

### Constraints

**`PRIMARY KEY`**

`id` uniquely identifies each user.

SQLite generates the identifier when a user is inserted. Other tables use this identifier when creating relationships with the user.

**`NOT NULL`**

`username` and `password` must exist for every persisted account.

`created_at` must also exist because every persisted user needs a creation timestamp.

The service layer validates usernames and passwords before persistence. The database constraints provide an additional integrity boundary.

**`UNIQUE`**

`username` must be unique so that two accounts cannot use the same username.

The database enforces this requirement even if concurrent registration attempts or programming errors bypass application-level checks.

The repository translates duplicate-username constraint violations into an appropriate typed exception. The Transport layer displays a readable message rather than exposing raw SQLite errors.

**`password`**

The application hashes passwords using Bcrypt.

The database stores the resulting hash, never the original password.

During login, the application verifies the supplied password against the stored hash. It must not compare plaintext passwords against database values or attempt to recover the original password.

Passwords and password hashes must never appear in application logs.

---

## 4. `listed_items`

The `listed_items` table stores items that users have listed for rental.

| Column | SQLite type | Constraints | Purpose |
|---|---|---|---|
| `id` | `INTEGER` | `PRIMARY KEY` | Database-generated item identifier |
| `owner_id` | `INTEGER` | `NOT NULL`, `FOREIGN KEY` | User who owns the item |
| `name` | `TEXT` | `NOT NULL` | Item name |
| `description` | `TEXT` | Nullable | Optional item description |
| `cost_per_day` | `TEXT` | `NOT NULL`, `CHECK` | Daily rental cost |
| `status` | `TEXT` | `NOT NULL`, `CHECK` | Current listing status |
| `created_at` | `TEXT` | `NOT NULL`, `DEFAULT` | Creation timestamp stored in UTC |

### Schema

```sql
CREATE TABLE IF NOT EXISTS listed_items (
                                            id INTEGER PRIMARY KEY,
                                            owner_id INTEGER NOT NULL,
                                            name TEXT NOT NULL,
                                            description TEXT,
                                            cost_per_day TEXT NOT NULL CHECK (
                                            cost_per_day NOT GLOB '*[^0-9.]*'
                                            AND length(cost_per_day) > 0
    AND length(cost_per_day)
    - length(replace(cost_per_day, '.', '')) <= 1
    AND substr(cost_per_day, 1, 1) GLOB '[0-9]'
    AND substr(cost_per_day, -1, 1) GLOB '[0-9]'
    AND replace(
                   replace(cost_per_day, '0', ''),
    '.',
    ''
    ) <> ''
    ),
    status TEXT NOT NULL CHECK (
                                   status IN ('available', 'rented', 'unlisted')
    ),
    created_at TEXT NOT NULL
    DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),

    FOREIGN KEY (owner_id)
    REFERENCES users(id)
    ON DELETE RESTRICT
    );
```

### `owner_id`

`owner_id` references `users.id`.

This ensures every listed item belongs to an existing user.

```sql
FOREIGN KEY (owner_id)
REFERENCES users(id)
ON DELETE RESTRICT
```

`ON DELETE RESTRICT` prevents a user from being deleted while they own listed items. This preserves ownership relationships and prevents orphaned item records.

The Service layer also enforces ownership rules. A user may list, delist, or relist only their own items. The user identity established by authentication determines ownership; callers must not be allowed to impersonate another owner by supplying an arbitrary user ID.

**Database referential integrity and service-level authorization serve different purposes:** the foreign key verifies that the owner exists, while the Service layer verifies that the authenticated user is authorized to perform an operation.

### `cost_per_day`

The Java domain represents rental costs using `BigDecimal`.

SQLite does not provide a dedicated decimal type, and `REAL` uses floating-point representation. To avoid losing decimal precision, `cost_per_day` is stored as `TEXT`.

The repository converts:

```text
BigDecimal
    ↓
toPlainString()
    ↓
SQLite TEXT
```

When reading:

```text
SQLite TEXT
    ↓
BigDecimal
```

The database `CHECK` constraint rejects values containing characters other than decimal digits and at most one decimal point. It also rejects empty values, values that do not begin or end with a digit, and values containing no non-zero digit.

The Service layer validates monetary values before persistence, including rejecting negative costs. The database constraint provides additional protection against invalid or non-positive stored values if application validation is bypassed.

### `status`

The database permits only these values:

```text
available
rented
unlisted
```

The `CHECK` constraint prevents arbitrary status values from being stored.

The Java `ItemStatus` enum maps to these database values.

The domain model controls which transitions are legal. The database validates the permitted states, while the domain and Service layers control how an item moves between them.

---

## 5. `rentals`

The `rentals` table stores rental transactions and rental history.

A rental represents a historical transaction, not merely the current state of an item.

When an item is rented again after a previous rental has been closed, a new rental row is created. Previous rental records are retained.

| Column | SQLite type | Constraints | Purpose |
|---|---|---|---|
| `id` | `INTEGER` | `PRIMARY KEY` | Database-generated rental identifier |
| `item_id` | `INTEGER` | `NOT NULL`, `FOREIGN KEY` | Item being rented |
| `renter_id` | `INTEGER` | `NOT NULL`, `FOREIGN KEY` | User renting the item |
| `start_time` | `TEXT` | `NOT NULL` | Rental start timestamp |
| `end_time` | `TEXT` | `NOT NULL`, `CHECK` | Rental end timestamp |
| `returned_at` | `TEXT` | Nullable | Time the item was returned |
| `status` | `TEXT` | `NOT NULL`, `CHECK` | Rental status |

### Schema

```sql
CREATE TABLE IF NOT EXISTS rentals (
                                       id INTEGER PRIMARY KEY,
                                       item_id INTEGER NOT NULL,
                                       renter_id INTEGER NOT NULL,
                                       start_time TEXT NOT NULL,
                                       end_time TEXT NOT NULL,
                                       returned_at TEXT,
                                       status TEXT NOT NULL CHECK (
                                       status IN ('active', 'closed')
    ),
    CHECK (end_time > start_time),

    FOREIGN KEY (item_id)
    REFERENCES listed_items(id)
    ON DELETE RESTRICT,

    FOREIGN KEY (renter_id)
    REFERENCES users(id)
    ON DELETE RESTRICT
    );
```

### `item_id`

`item_id` references `listed_items.id`.

This prevents a rental from referring to an item that does not exist.

```sql
FOREIGN KEY (item_id)
REFERENCES listed_items(id)
ON DELETE RESTRICT
```

`ON DELETE RESTRICT` prevents an item from being deleted while it has rental records. Rental history remains associated with the item.

### `renter_id`

`renter_id` references `users.id`.

This ensures every rental has an existing user as the renter.

```sql
FOREIGN KEY (renter_id)
REFERENCES users(id)
ON DELETE RESTRICT
```

`ON DELETE RESTRICT` prevents a user from being deleted while they are referenced by rental records. This preserves historical rental information.

### `start_time` and `end_time`

Both timestamps are required.

The database enforces:

```sql
CHECK (end_time > start_time)
```

This prevents zero-length and negative-length rentals at the database level.

For example, the following is invalid:

```text
start_time = 2026-10-07T10:00:00.000Z
end_time   = 2026-10-07T10:00:00.000Z
```

The following is also invalid:

```text
start_time = 2026-10-07T11:00:00.000Z
end_time   = 2026-10-07T10:00:00.000Z
```

The Java application validates that rental duration is at least one day before persistence. The database constraint provides an additional integrity check.

### `returned_at`

`returned_at` is nullable because an active rental has not yet been returned.

For an active rental:

```text
status = active
returned_at = NULL
```

For a closed rental:

```text
status = closed
returned_at = return timestamp
```

The Service layer is responsible for ensuring the rental lifecycle is handled consistently.

### `status`

The database permits only:

```text
active
closed
```

The `CHECK` constraint prevents invalid rental states from being stored.

The Java `RentalStatus` enum maps to these database values.

---

## 6. One Active Rental Per Item

The database contains a partial unique index:

```sql
CREATE UNIQUE INDEX IF NOT EXISTS
    idx_rentals_one_active_per_item
    ON rentals(item_id)
    WHERE status = 'active';
```

This enforces the rule that an item can have at most one active rental at a time.

Uniqueness applies only to rows where:

```text
status = 'active'
```

Multiple historical `closed` rentals for the same item are allowed.

For example, this is valid:

```text
item 10 → rental 1 → closed
item 10 → rental 2 → closed
item 10 → rental 3 → active
```

This is rejected:

```text
item 10 → rental 1 → active
item 10 → rental 2 → active
```

The database enforces this invariant rather than relying exclusively on application logic. This protects against programming errors and conflicting database operations.

Rental creation and the associated item-status update must also take place in one transaction. Both changes must commit together or roll back together.

---

## 7. Relationships

### User → Listed Items

One user can own many listed items.

```text
users.id
    │
    └──< listed_items.owner_id
```

The relationship is implemented by:

```sql
FOREIGN KEY (owner_id)
REFERENCES users(id)
ON DELETE RESTRICT
```

Every listed item must have an existing owner.

### Listed Item → Rentals

One listed item can have many rental records over its lifetime.

```text
listed_items.id
    │
    └──< rentals.item_id
```

The relationship is implemented by:

```sql
FOREIGN KEY (item_id)
REFERENCES listed_items(id)
ON DELETE RESTRICT
```

An item can therefore have multiple closed rentals and, at most, one active rental.

### User → Rentals as Renter

One user can have many rentals.

```text
users.id
    │
    └──< rentals.renter_id
```

The relationship is implemented by:

```sql
FOREIGN KEY (renter_id)
REFERENCES users(id)
ON DELETE RESTRICT
```

The `users` table participates in two distinct relationships: item ownership through `listed_items.owner_id` and rental participation through `rentals.renter_id`.

---

## 8. Constraint Summary

| Constraint | Table or column | Purpose |
|---|---|---|
| `PRIMARY KEY` | All three tables | Uniquely identifies each record |
| `NOT NULL` | Required columns | Prevents required data from being absent |
| `UNIQUE` | `users.username` | Prevents duplicate usernames |
| `FOREIGN KEY` | `listed_items.owner_id` | Ensures every item has an existing owner |
| `FOREIGN KEY` | `rentals.item_id` | Ensures every rental references an existing item |
| `FOREIGN KEY` | `rentals.renter_id` | Ensures every rental references an existing renter |
| `ON DELETE RESTRICT` | Foreign keys | Prevents deletion of referenced records |
| `CHECK` | `listed_items.cost_per_day` | Rejects invalid or non-positive monetary values |
| `CHECK` | `listed_items.status` | Restricts item status to known values |
| `CHECK` | `rentals.status` | Restricts rental status to known values |
| `CHECK` | `rentals.end_time` | Requires a positive rental duration |
| Partial unique index | `rentals.item_id` | Allows at most one active rental per item |

The database provides integrity protection at several levels:

```text
Primary keys
    ↓
Record identity

Foreign keys
    ↓
Relationship integrity

NOT NULL
    ↓
Required data

UNIQUE
    ↓
Uniqueness

CHECK
    ↓
Valid values

Partial unique index
    ↓
Cross-row business invariant
```

These database rules complement Service-layer validation and authorization; they do not replace them.

---

## 9. Timestamp Format and Time Zone

Timestamps are stored as SQLite `TEXT`.

Database-generated timestamps use:

```sql
strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
```

This produces an ISO-8601 UTC timestamp such as:

```text
2026-09-30T08:15:42.123Z
```

The `Z` indicates UTC.

Application timestamps are treated as UTC before being persisted by the SQLite repositories. Timestamps read from SQLite are parsed back into Java date/time types.

A consistent UTC representation avoids storing different local time zones across records and makes timestamp comparison and ordering predictable.

---

## 10. Schema Initialization and Safe Repeated Startup

The database schema is defined in `src/main/resources/schema.sql` and applied by `DatabaseManager.initialize()`.

Initialization must support both a new database and an existing database without failing simply because its tables or indexes already exist.

The initialization process is:

```text
Configured database Path
        │
        ▼
DatabaseManager
        │
        ▼
Create SQLite connection
        │
        ▼
Enable PRAGMA foreign_keys = ON
        │
        ▼
Load /schema.sql from the classpath
        │
        ▼
Execute idempotent schema statements
        │
        ▼
Ensure tables and indexes exist
```

The schema file is loaded using:

```java
DatabaseManager.class.getResourceAsStream("/schema.sql")
```

The SQL is read as UTF-8 and executed against the SQLite connection.

`DatabaseManager` creates the connection required for initialization and closes it when initialization completes.

### Migration policy for the multi-user schema

`DatabaseManager` uses schema version 3. SQLite's `CREATE TABLE IF NOT EXISTS` does not alter an existing table, so an
old single-user database without `users.password` cannot be upgraded in place. A database is also considered legacy if
it contains an empty password value left by the earlier intermediate migration.

When legacy data is detected, `DatabaseManager` explicitly drops the application tables (`rentals`, `listed_items`,
and `users`) and recreates the complete schema in one transaction. It does not leave legacy usernames with empty or
invented credentials. This is a deliberate destructive reset: old accounts and rental history must be recreated or
seeded for the multi-user application. Normal startup on an already-current database does not drop or overwrite data.

The alternative was preserving legacy rows with an empty unusable hash or rebuilding only the users table. That keeps
more data but leaves usernames that cannot authenticate and creates ambiguous recovery behavior. The team chose a
clear reset because the old accounts have no valid credentials and the project is still at the migration stage. The
trade-off is explicit loss of legacy data, so the Docker upgrade procedure must back up or remove the old volume before
adopting this schema version.

Foreign-key enforcement is explicitly enabled on every connection:

```sql
PRAGMA foreign_keys = ON;
```


### Seed-data initialization

Development seed data must also be safe to apply repeatedly.

The initialization process must not insert duplicate users, items, or rental records on every launch. Use a deliberate, idempotent seed-data strategy, such as stable seed identifiers with conflict handling or checking whether the required seed records already exist.

Seed initialization must preserve existing current-schema user data and rental history. It must not delete and recreate
populated current-schema tables to restore demo data. The separate legacy-schema reset described above is the only
intentional destructive initialization path.

Schema initialization and seed-data initialization are separate responsibilities: schema statements establish the database structure, while seed logic supplies the required development/demo records.

---

## 11. Database Configuration

The database location is configurable.

`DatabaseManager` receives a `java.nio.file.Path`:

```java
DatabaseManager databaseManager =
        new DatabaseManager(databasePath);
```

The JDBC URL is derived internally:

```java
String databaseUrl = "jdbc:sqlite:" + databasePath;
```

Callers do not need to construct SQLite JDBC URLs directly.

The configuration flow is:

```text
Application configuration
        │
        ▼
Main
        │
        ▼
Path databasePath
        │
        ▼
DatabaseManager(databasePath)
        │
        ▼
jdbc:sqlite:<databasePath>
        │
        ▼
SQLite database
```

`Main` owns application configuration and CLI argument parsing.

`DatabaseManager` owns SQLite connection creation and configuration.

Repositories obtain connections through `DatabaseManager` rather than receiving the database path or JDBC URL directly.

This separates application configuration from persistence and allows the database location to change without modifying repository implementations.

---

## 12. Connection and Transaction Lifecycle

`DatabaseManager` does not maintain a single application-wide database connection.

For normal repository operations:

```text
Repository method
      │
      ▼
DatabaseManager.getConnection()
      │
      ▼
Perform SQL operation
      │
      ▼
Release connection
```

The connection is released when the operation completes. Resources must also be released when an operation fails.

For transactions, the transaction owns the connection:

```text
Service
   │
   ▼
Transactor.inTransaction(...)
   │
   ▼
Transaction-owned connection
   │
   ├── Repository A
   ├── Repository B
   └── Repository C
   │
   ▼
Commit or rollback
   │
   ▼
Close transaction connection
```

Repositories participating in a transaction reuse the same connection and must not close it prematurely.

`DatabaseManager.releaseConnection()` releases normal connections while leaving an active transaction connection open until the transaction finishes.

Repositories do not commit or roll back transactions themselves.

Transaction boundaries are owned by the Service layer through the `Transactor` interface.

A successful transaction commits. If a `RuntimeException` is thrown during the transaction, the transaction rolls back and the exception is rethrown.

Nested transaction calls join the existing transaction rather than creating a separate independent transaction.

This ensures that operations involving multiple database changes, such as creating a rental and updating item status, succeed or fail atomically.

Connection and transaction failures must be logged with useful internal context. User-facing messages must not expose raw SQL errors or stack traces.

---

## 13. Docker and Persistent Database Storage

The application includes a `Dockerfile` for building and running the CLI application in a container.

The SQLite database must be stored at a path backed by a persistent Docker volume. The application configuration, Docker configuration, and run instructions must agree on that path.

For example, the container can use:

```text
/data/rental-tracker.db
```

with a named volume mounted at `/data`.

A representative run command is:

```bash
docker run -it \
  --name rental-tracker \
  -v rental-tracker-data:/data \
  multi-user-rental-tracker
```

The final command and image configuration must match the actual `Dockerfile` and database-path configuration committed to the repository.

### Persistence requirements

- The database file must be stored inside the mounted volume, not only in the container's writable filesystem.
- User accounts, password hashes, listed items, and rental history must survive container stops and restarts.
- Recreating a container while reusing the same named volume must preserve the database.
- Schema initialization must succeed when the volume contains no database.
- Subsequent launches must not fail because existing tables or indexes already exist.
- Initialization must not overwrite or erase existing current-schema records. A legacy schema is the documented
  exception and is reset explicitly rather than partially migrated.
- Database connections must be closed cleanly on application shutdown.

A named Docker volume persists independently of the container. Removing a container does not normally remove the named volume, but explicitly deleting the volume will remove its persisted database data.

The database path must therefore be configurable so local development and Docker deployment can use suitable locations without changing repository code.

---

## 14. Seed Database

The development seed database is located at:

```text
data/rental-tracker.db
```

When using Docker, the active database must instead be located at the configured path backed by the persistent volume, for example:

```text
/data/rental-tracker.db
```

The seed database must contain at least two users and six listed items distributed among those users.

Seed data must satisfy all database constraints, including:

- Valid user references.
- Valid item ownership.
- Valid rental references.
- Valid item statuses.
- Valid rental statuses.
- Valid positive rental costs.
- Valid rental time ranges.
- At most one active rental per item.

The seed database is development/demo data. Seeded accounts must use password hashes, not plaintext passwords. Any documented demo credentials must be clearly identified as demo-only and must not be reused for real accounts.

### Seed-data creation

1. Create a fresh SQLite database.
2. Apply `src/main/resources/schema.sql`.
3. Insert at least two valid users with password hashes.
4. Insert at least six listed items distributed among the users.
5. Insert any required rental/demo records.
6. Verify that all database constraints pass.
7. Save the development seed database as `data/rental-tracker.db`, if the team maintains a checked-in seed database.

If the application initializes a database at runtime, it must use an idempotent seed strategy rather than blindly reinserting the same records on every launch.

The seed data must not overwrite existing account credentials, items, or rental history.

`schema.sql` remains the authoritative definition of the database structure and constraints. The seed database is data, not a second schema definition.

---

## 15. Testing the Persistence Layer

Persistence tests use real SQLite databases rather than mocked database connections.

Each test should use an isolated temporary database, such as one created with JUnit `@TempDir`, so that tests do not depend on data left behind by other tests.

The general test setup is:

```text
Test
  │
  ▼
Temporary database path
  │
  ▼
DatabaseManager
  │
  ▼
initialize()
  │
  ▼
Real SQLite database
  │
  ▼
Repository
```

Tests should construct the data they need through the repository layer rather than depending on a shared pre-populated database.

### User persistence tests

Important cases include:

- Creating a user with a password hash.
- Retrieving a user.
- Retrieving a missing user.
- Preventing duplicate usernames.
- Rejecting missing required fields.
- Ensuring plaintext passwords are not stored.

### Item persistence tests

Important cases include:

- Creating an item.
- Retrieving an item.
- Retrieving items by owner.
- Preventing invalid item statuses.
- Preventing invalid rental costs.
- Rejecting an item whose owner does not exist.
- Preventing deletion of referenced owners.

### Rental persistence tests

Important cases include:

- Creating an active rental.
- Creating a closed rental.
- Retrieving a rental.
- Retrieving rental history.
- Rejecting a rental for a missing item.
- Rejecting a rental for a missing renter.
- Rejecting invalid rental durations.
- Preventing more than one active rental for the same item.
- Preserving closed rental history.

### Schema initialization tests

Initialization tests must verify that:

- An empty database is initialized successfully.
- All required tables and indexes exist after initialization.
- Running initialization more than once does not fail because tables or indexes already exist.
- Repeated seed initialization does not create duplicate seed records.
- Existing users, items, and rental history are preserved.
- Foreign-key enforcement is enabled on each connection.

These tests verify that startup is safe for both a new database and a database containing existing application data.

### Transaction tests

Transaction tests use real SQLite databases and cover:

- Successful transactions being committed.
- `RuntimeException` causing rollback.
- Nested transactions joining the outer transaction.
- Failures in nested operations causing the outer transaction to roll back.
- Rental creation and item-status changes being atomic.

### Connection lifecycle tests

Connection management should be verified.

For normal operations:

```text
Create/acquire connection
        ↓
Execute operation
        ↓
Release connection
```

For transactions:

```text
Begin transaction
        ↓
Acquire transaction connection
        ↓
Multiple repository operations
        ↓
Commit / rollback
        ↓
Close transaction connection
```

A repository participating in an active transaction must not prematurely close the shared transaction connection.

Persistence tests remain independent of the CLI and `Main`. This allows the database layer to be tested independently before the application is fully wired.

---

## 16. ER Diagram Reference

The visual ER diagram is stored at:

```text
docs/er-diagram.svg
```

The diagram represents the three tables and their foreign-key relationships:

```text
users
  │
  ├──< listed_items
  │       │
  │       └──< rentals
  │
  └──< rentals
```

The `users` entity participates in rentals in two roles:

- Owner of the rented item.
- Renter of the item.

The diagram must remain synchronized with `schema.sql`.

Whenever a table, relationship, foreign key, or important database constraint changes, the ER diagram must be reviewed to ensure that it accurately represents the implemented schema.

---

## 17. Database Implementation Status

The database layer provides the persistence foundation for authentication, item listings, rental transactions, and rental history.

### Completed

Mark an item as complete only when its implementation and relevant tests have been verified.

- [ ] SQLite database integration.
- [ ] Configurable database path.
- [ ] `DatabaseManager`.
- [ ] `users` table with password-hash storage.
- [ ] `listed_items` table.
- [ ] `rentals` table.
- [ ] Idempotent table and index creation.
- [ ] Primary keys.
- [ ] Foreign keys.
- [ ] `ON DELETE RESTRICT`.
- [ ] Username uniqueness.
- [ ] Item status constraints.
- [ ] Rental status constraints.
- [ ] Monetary value validation.
- [ ] Rental duration validation.
- [ ] One-active-rental-per-item constraint.
- [ ] UTC timestamp storage.
- [ ] Safe schema initialization on repeated launches.
- [ ] Idempotent seed-data initialization.
- [ ] Explicit SQLite foreign-key enforcement.
- [ ] Repository connection lifecycle.
- [ ] Transaction-aware connection management.
- [ ] Commit and rollback handling.
- [ ] Nested transaction handling.
- [ ] Seed data with at least two users and six listed items.
- [ ] Persistence testing using real SQLite.
- [ ] Docker configuration with persistent database storage.
- [ ] Persistence verification after container restart.

### Remaining / Integration Work

The database layer must integrate with the rest of the application while preserving its integrity guarantees.

1. Complete and verify service-layer workflows for authentication, item management, rentals, and returns.
2. Ensure service operations use repository interfaces rather than accessing SQLite directly.
3. Verify transaction boundaries around operations that modify multiple records.
4. Ensure authenticated identity and ownership are enforced by the Service layer.
5. Integrate repositories and services with the CLI.
6. Verify structured logging and ensure credentials never appear in logs.
7. Run end-to-end tests through the application flow.
8. Verify that data survives Docker container restarts.
9. Keep `schema.sql`, the ER diagram, and this document synchronized whenever the database design changes.

Additional tables or constraints should be introduced when required by an agreed application requirement. Database changes must preserve existing data through an appropriate migration strategy.

The database design provides the relational structure, account storage, integrity constraints, rental history, transaction support, and persistent storage required by Multi-user Rental Tracker.

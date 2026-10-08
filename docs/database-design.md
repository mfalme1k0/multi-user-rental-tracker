# Database Design

**Owner:** Fidel

## 1. Overview

Rental Tracker uses SQLite as its relational database.

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

`Main` is responsible for determining the configured database path and passing it to `DatabaseManager`. CLI argument parsing does not belong in the persistence layer.

SQLite foreign-key enforcement is explicitly enabled for every database connection.

The database contains three tables:

* `users`
* `listed_items`
* `rentals`

The relationships between these tables represent users who own listed items, users who rent those items, and the rental history of each item.

The database also enforces important domain invariants at the persistence level. These include valid item and rental statuses, valid rental durations, referential integrity, valid monetary values, and at most one active rental per item.

---

## 2. Entity Relationship Diagram

The database relationships are also represented in:

```text
docs/er-diagram.png
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

The `users` table therefore participates in the rental relationship in two different roles:

* as the owner of the rented item
* as the renter of the item

---

## 3. `users`

The `users` table stores users of the application.

| Column       | SQLite type | Constraints           | Purpose                            |
| ------------ | ----------- | --------------------- | ---------------------------------- |
| `id`         | `INTEGER`   | `PRIMARY KEY`         | Database-generated user identifier |
| `username`   | `TEXT`      | `NOT NULL`, `UNIQUE`  | User's unique username             |
| `created_at` | `TEXT`      | `NOT NULL`, `DEFAULT` | Creation timestamp stored in UTC   |

### Schema

```sql
CREATE TABLE users (
    id INTEGER PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    created_at TEXT NOT NULL
        DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);
```

### Constraints

#### `PRIMARY KEY`

`id` uniquely identifies each user.

SQLite generates the identifier when a new user is inserted.

The identifier is used by other tables when creating relationships with the user.

#### `NOT NULL`

`username` must exist because a user cannot be identified without a username.

`created_at` must also exist because every persisted user needs a creation timestamp.

#### `UNIQUE`

`username` must be unique so that two users cannot have the same username.

Relationships between users and other records use the stable numeric `id` rather than the username. This means that a username can be changed without requiring foreign-key references to be updated.

---

## 4. `listed_items`

The `listed_items` table stores items that users have listed for rental.

| Column         | SQLite type | Constraints               | Purpose                            |
| -------------- | ----------- | ------------------------- | ---------------------------------- |
| `id`           | `INTEGER`   | `PRIMARY KEY`             | Database-generated item identifier |
| `owner_id`     | `INTEGER`   | `NOT NULL`, `FOREIGN KEY` | User who owns the item             |
| `name`         | `TEXT`      | `NOT NULL`                | Item name                          |
| `description`  | `TEXT`      | Nullable                  | Optional item description          |
| `cost_per_day` | `TEXT`      | `NOT NULL`, `CHECK`       | Daily rental cost                  |
| `status`       | `TEXT`      | `NOT NULL`, `CHECK`       | Current listing status             |
| `created_at`   | `TEXT`      | `NOT NULL`, `DEFAULT`     | Creation timestamp stored in UTC   |

### Schema

```sql
CREATE TABLE listed_items (
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

This ensures that every listed item belongs to an existing user.

```sql
FOREIGN KEY (owner_id)
REFERENCES users(id)
ON DELETE RESTRICT
```

`ON DELETE RESTRICT` prevents a user from being deleted while they still own listed items.

This preserves the ownership relationship and prevents orphaned item records.

### `cost_per_day`

The domain represents rental costs using Java `BigDecimal`.

SQLite does not provide a dedicated decimal type, and SQLite `REAL` uses floating-point representation. To avoid losing decimal precision, `cost_per_day` is stored as `TEXT`.

The repository converts:

```text
BigDecimal
    ↓
toPlainString()
    ↓
SQLite TEXT
```

and converts it back when reading:

```text
SQLite TEXT
    ↓
BigDecimal
```

The database `CHECK` constraint ensures that the stored value:

* contains only decimal digits and at most one decimal point
* is not empty
* begins with a digit
* ends with a digit
* contains at least one non-zero digit

This provides database-level protection against invalid or non-positive monetary values even if Java-side validation is bypassed.

Keeping the database representation as `TEXT` also avoids relying on floating-point storage for monetary values.

### `status`

The database permits only these values:

```text
available
rented
unlisted
```

The `CHECK` constraint prevents arbitrary status values from being stored.

The Java `ItemStatus` enum maps to these database values.

The legality of transitions between statuses is handled by the domain model rather than by the database.

The database therefore validates the set of possible states, while the application controls how an item moves between those states.

---

## 5. `rentals`

The `rentals` table stores rental transactions and rental history.

A rental represents a historical transaction rather than simply the current state of an item.

When an item is rented again after a previous rental has been closed, a new rental row is created. Previous rental records are retained.

| Column        | SQLite type | Constraints               | Purpose                              |
| ------------- | ----------- | ------------------------- | ------------------------------------ |
| `id`          | `INTEGER`   | `PRIMARY KEY`             | Database-generated rental identifier |
| `item_id`     | `INTEGER`   | `NOT NULL`, `FOREIGN KEY` | Item being rented                    |
| `renter_id`   | `INTEGER`   | `NOT NULL`, `FOREIGN KEY` | User renting the item                |
| `start_time`  | `TEXT`      | `NOT NULL`                | Rental start timestamp               |
| `end_time`    | `TEXT`      | `NOT NULL`, `CHECK`       | Rental end timestamp                 |
| `returned_at` | `TEXT`      | Nullable                  | Time the item was returned           |
| `status`      | `TEXT`      | `NOT NULL`, `CHECK`       | Rental status                        |

### Schema

```sql
CREATE TABLE rentals (
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

`ON DELETE RESTRICT` prevents an item from being deleted while it has rental records.

Rental records form part of an item's history and should not disappear simply because an item is no longer listed.

### `renter_id`

`renter_id` references `users.id`.

This ensures that every rental has an existing user as the renter.

```sql
FOREIGN KEY (renter_id)
REFERENCES users(id)
ON DELETE RESTRICT
```

`ON DELETE RESTRICT` prevents a user from being deleted while they are referenced by rental records.

This preserves historical rental information.

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

and:

```text
start_time = 2026-10-07T11:00:00.000Z
end_time   = 2026-10-07T10:00:00.000Z
```

The Java application also validates rental duration before persistence.

The database constraint acts as a second line of protection.

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

The service/domain layer is responsible for ensuring that the lifecycle of the rental is handled consistently.

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
CREATE UNIQUE INDEX idx_rentals_one_active_per_item
    ON rentals(item_id)
    WHERE status = 'active';
```

This enforces the rule that an item can have at most one active rental at a time.

This is a partial unique index because uniqueness applies only to rows where:

```text
status = 'active'
```

Multiple historical `closed` rentals for the same item are therefore allowed.

For example, this is valid:

```text
item 10 → rental 1 → closed
item 10 → rental 2 → closed
item 10 → rental 3 → active
```

But this is rejected:

```text
item 10 → rental 1 → active
item 10 → rental 2 → active
```

The database therefore enforces the invariant rather than relying only on application logic.

This is important because application-level checks alone can be bypassed by a programming error or by another database operation.

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

This means every listed item must have an existing owner.

A user cannot be deleted while they still own listed items.

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

This allows rental history to remain associated with the item.

An item can therefore have:

```text
closed rental
closed rental
closed rental
active rental
```

over its lifetime.

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

The `users` table therefore participates in rentals in two different roles:

* as the owner of the rented item through `listed_items.owner_id`
* as the renter through `rentals.renter_id`

These are separate relationships even though both ultimately reference `users.id`.

---

## 8. Constraint Summary

| Constraint           | Table                              | Purpose                                            |
| -------------------- | ---------------------------------- | -------------------------------------------------- |
| `PRIMARY KEY`        | `users`, `listed_items`, `rentals` | Uniquely identifies each record                    |
| `NOT NULL`           | Required columns                   | Prevents required data from being absent           |
| `UNIQUE`             | `users.username`                   | Prevents duplicate usernames                       |
| `FOREIGN KEY`        | `listed_items.owner_id`            | Ensures every item has an existing owner           |
| `FOREIGN KEY`        | `rentals.item_id`                  | Ensures every rental references an existing item   |
| `FOREIGN KEY`        | `rentals.renter_id`                | Ensures every rental references an existing renter |
| `ON DELETE RESTRICT` | Foreign keys                       | Prevents deletion of referenced records            |
| `CHECK`              | `listed_items.cost_per_day`        | Prevents invalid/non-positive monetary values      |
| `CHECK`              | `listed_items.status`              | Restricts item status to known values              |
| `CHECK`              | `rentals.status`                   | Restricts rental status to known values            |
| `CHECK`              | `rentals.end_time`                 | Requires the rental to have a positive duration    |
| Partial unique index | `rentals.item_id`                  | Allows at most one active rental per item          |

The database therefore provides integrity protection at several levels:

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

Application timestamps are treated as UTC before being persisted by the SQLite repositories.

When timestamps are read from SQLite, they are parsed from the stored ISO-8601 representation back into Java date/time types.

Using a consistent UTC representation avoids storing different local time zones in different records and makes timestamp comparison and ordering predictable.

The database therefore has a single timestamp representation regardless of the local time zone of the machine running the application.

---

## 10. Schema Initialization

The database schema is applied by `DatabaseManager.initialize()`.

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
Execute schema SQL
        │
        ▼
Tables and indexes are created
```

The schema file is loaded using:

```java
DatabaseManager.class.getResourceAsStream("/schema.sql")
```

The SQL is read as UTF-8 and executed against the SQLite connection.

`DatabaseManager` creates the connection required for initialization and closes it when initialization completes.

Foreign-key enforcement is explicitly enabled whenever a connection is created:

```sql
PRAGMA foreign_keys = ON;
```

This is necessary because SQLite foreign-key enforcement must be enabled for each connection.

The database initialization process therefore ensures that the same schema and foreign-key behavior are established whenever a new database is created.

---

## 11. Database Configuration

The database location is configurable.

`DatabaseManager` receives the database location as a `java.nio.file.Path`:

```java
DatabaseManager databaseManager =
        new DatabaseManager(databasePath);
```

The JDBC URL is derived internally by `DatabaseManager`:

```java
String databaseUrl = "jdbc:sqlite:" + databasePath;
```

Therefore, callers do not need to construct SQLite JDBC URLs directly.

The intended configuration flow is:

```text
CLI configuration
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

Repositories do not receive the database path or JDBC URL directly. They obtain connections through `DatabaseManager`.

This keeps CLI concerns separate from persistence concerns while allowing the database location to be changed without modifying the persistence implementation.

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
DatabaseManager.releaseConnection()
```

The connection is released after the repository operation completes.

If the operation is not running inside a transaction, the repository obtains its own connection and releases it when the operation finishes.

This prevents normal repository operations from leaving connections open unnecessarily.

For operations executed inside a transaction, the transaction owns the connection:

```text
Service
   │
   ▼
Transactor.inTransaction(...)
   │
   ▼
DatabaseManager.beginTransaction()
   │
   ▼
Transaction-owned connection
   │
   ├── Repository A
   │
   ├── Repository B
   │
   └── Repository C
   │
   ▼
Commit or rollback
   │
   ▼
Close transaction connection
```

Repositories participating in the transaction reuse the same connection.

Repository methods must not close the transaction-owned connection.

`DatabaseManager.releaseConnection()` therefore releases normal connections while leaving the active transaction connection open until the transaction finishes.

Repositories do not commit or roll back transactions themselves.

Transaction boundaries are owned by the service layer through the `Transactor` interface.

A successful transaction is committed.

If a `RuntimeException` is thrown during the transaction, the transaction is rolled back and the exception is rethrown.

Nested transaction calls join the existing transaction rather than creating a second independent transaction.

This keeps the transaction boundary at the application/service level while allowing multiple repository operations to participate in the same atomic database operation.

---

## 13. Seed Database

The development seed database is located at:

```text
data/rental-tracker.db
```

The seed database must contain at least six listed items.

The seed database is based on the same schema defined in:

```text
src/main/resources/schema.sql
```

The seed data must satisfy all database constraints, including:

* valid user references
* valid item ownership
* valid rental references
* valid item statuses
* valid rental statuses
* valid positive rental costs
* valid rental time ranges
* the one-active-rental-per-item rule

The intended process for producing the seed database is:

1. Create a fresh SQLite database.
2. Apply `src/main/resources/schema.sql`.
3. Insert valid users.
4. Insert at least six listed items.
5. Insert any required rental/demo records.
6. Verify that all database constraints pass.
7. Save the resulting database as:

```text
data/rental-tracker.db
```

The seeded database is development/demo data.

`schema.sql` remains the authoritative definition of the database structure and constraints.

The seed database should therefore not be treated as a second schema definition.

---

## 14. Testing the Persistence Layer

Persistence tests use real SQLite databases rather than mocked database connections.

Each test should use an isolated temporary database so that tests do not depend on data left behind by other tests.

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

This makes tests deterministic and prevents one test from influencing another.

### User persistence tests

Important cases include:

* creating a user
* retrieving a user
* retrieving a missing user
* preventing duplicate usernames

### Item persistence tests

Important cases include:

* creating an item
* retrieving an item
* retrieving items by owner
* preventing invalid item statuses
* preventing invalid rental costs
* rejecting an item whose owner does not exist
* preventing deletion of referenced owners

### Rental persistence tests

Important cases include:

* creating an active rental
* creating a closed rental
* retrieving a rental
* retrieving rental history
* rejecting a rental for a missing item
* rejecting a rental for a missing renter
* rejecting invalid rental durations
* preventing more than one active rental for the same item
* preserving closed rental history

### Transaction tests

Transaction behaviour is tested using real SQLite databases.

The transaction tests cover:

* successful transactions being committed
* `RuntimeException` causing rollback
* nested transactions joining the outer transaction
* failures in nested transactions causing the outer transaction to roll back

### Connection lifecycle tests

Connection management should also be verified.

The expected behavior is:

```text
Normal repository operation
        ↓
Create/acquire connection
        ↓
Execute operation
        ↓
Release connection
```

while a transaction behaves as:

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

Persistence tests should remain independent from the CLI and `Main`.

This allows the persistence layer to be tested and verified before the application composition root is wired.

---

## 15. ER Diagram Reference

The visual ER diagram is stored at:

```text
docs/er-diagram.png
```

The diagram represents the three tables and their foreign-key relationships.

The relationships are:

```text
users
  │
  ├──< listed_items
  │       │
  │       └──< rentals
  │
  └──< rentals
```

The `users` entity participates in rentals in two different roles:

* owner of the rented item
* renter of the item

The diagram should remain synchronized with `schema.sql`.

Whenever a table, relationship, foreign key, or important database constraint is changed, the ER diagram should be reviewed to ensure that it still represents the implemented schema.

---

## Database Implementation Status

The database layer now provides the persistence foundation required by the rest of the application.

### Completed

* [x] SQLite database integration
* [x] Configurable database path
* [x] `DatabaseManager`
* [x] `users` table
* [x] `listed_items` table
* [x] `rentals` table
* [x] Primary keys
* [x] Foreign keys
* [x] `ON DELETE RESTRICT`
* [x] Username uniqueness
* [x] Item status constraints
* [x] Rental status constraints
* [x] Monetary value validation
* [x] Rental duration validation
* [x] One-active-rental-per-item constraint
* [x] UTC timestamp storage
* [x] Schema initialization
* [x] Explicit SQLite foreign-key enforcement
* [x] Repository connection lifecycle
* [x] Transaction-aware connection management
* [x] Commit and rollback handling
* [x] Nested transaction handling
* [x] Seed database
* [x] Persistence testing using SQLite

### Remaining / Integration Work

The database layer should now primarily be treated as a foundation for the remaining application layers.

The next work should focus on:

1. Completing and verifying service-layer workflows.
2. Ensuring service operations use the repository interfaces rather than accessing SQLite directly.
3. Verifying transaction boundaries around operations that modify multiple records.
4. Integrating the repositories and services with the CLI.
5. Running end-to-end tests through the application flow.
6. Keeping `schema.sql`, the ER diagram, and this document synchronized if the domain changes.

The database layer should not be expanded with additional tables or constraints unless a new application requirement requires them.

The current schema provides the relational structure, integrity constraints, historical rental records, and transaction support required by the current Rental Tracker domain.

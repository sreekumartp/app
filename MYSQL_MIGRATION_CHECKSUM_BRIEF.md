# Brief for Claude Code: Collation-safe MySQL migration checksums in GitHub Actions

> Hand this whole file to Claude Code (for example `claude "Implement the brief in MYSQL_MIGRATION_CHECKSUM_BRIEF.md"`).
> It is self-contained: it explains the problem, the required design, what to deliver and how to tell when the work is done.

---

## 1. Context

- I have scripts that migrate MySQL tables, for example between servers, between MySQL versions (5.7 to 8.0), or from `latin1`/`utf8` to `utf8mb4`.
- The migration runs in **GitHub Actions**.
- To confirm nothing was lost or corrupted, I compute a **hash/checksum of the data before and after** the migration and compare the two.
- **The problem:** the check **fails often because of collation and charset differences**, even when the data is actually correct. I need a check that is robust, deterministic and industry-standard, and that only fails when the data really differs.

## 2. Root causes the solution must remove

Every one of these must be neutralized by the design:

1. **Row-order dependence.** Collations order rows differently (`utf8mb4_general_ci` vs `utf8mb4_0900_ai_ci`), and case-insensitive ties (`'abc'` and `'ABC'`) come back in no fixed order. A hash computed over an ordered stream of rows is therefore unstable.
2. **Byte encoding.** The same text has different bytes in `latin1` and `utf8mb4`, so a hash of the raw column changes when the charset changes.
3. **Connection and session settings.** `character_set_client/results`, `collation_connection` and `time_zone` can convert values before they are hashed. `TIMESTAMP` values display differently in different session time zones.
4. **Pad attribute.** The `0900` collations are `NO PAD` and older ones are `PAD SPACE`, so trailing spaces behave differently in `DISTINCT`, `GROUP BY` and comparisons.
5. **"Illegal mix of collations" errors** when `CONCAT` combines columns that have different collations.
6. **`CHECKSUM TABLE`** depends on storage and row format and is **not** valid across versions or schema changes. **Do not use it.**
7. **NULL handling.** `CONCAT()` returns NULL if any argument is NULL, and NULL must not hash the same as an empty string.
8. **Formatting differences** for `FLOAT`/`DOUBLE` (display changed in MySQL 8.0.17), `DATETIME` fractional seconds and `JSON`.
9. **Live writes** during the checksum produce real but misleading mismatches.
10. **Double-encoded data,** meaning UTF-8 bytes stored in `latin1` columns. This is a real data problem that the tool should help *detect*, not hide.

## 3. Required design

### 3.1 Canonical row representation

Hash a **canonical byte form** of each row, never the collated string MySQL displays.

| Column type | Canonical expression |
|---|---|
| `CHAR`, `VARCHAR`, `*TEXT`, `ENUM`, `SET` | `HEX(CONVERT(col USING utf8mb4))` |
| `BINARY`, `VARBINARY`, `*BLOB` | `HEX(col)` |
| `DATETIME`, `TIMESTAMP` | `DATE_FORMAT(col, '%Y-%m-%d %H:%i:%s.%f')` (session `time_zone='+00:00'`) |
| `DATE`, `TIME`, `YEAR` | `CAST(col AS CHAR)` |
| `JSON` | `HEX(CAST(col AS CHAR CHARACTER SET utf8mb4))` (consider normalizing key order if JSON is rewritten) |
| `FLOAT`, `DOUBLE` | `CAST(ROUND(col, <precision>) AS CHAR)`, precision configurable (default 6) |
| `DECIMAL`, integers, `BIT` | `CAST(col AS CHAR)` (`BIT` → `HEX(col)` or `CAST(col+0 AS CHAR)`) |
| Everything else | `CAST(col AS CHAR)` |

- Wrap every expression in `COALESCE(<expr>, 'NULL!')`. Hex output can never contain `NULL!`, so NULL and the empty string can't collide.
- Join columns with `CONCAT_WS('#', ...)`.
- Order columns **by column name**, not by ordinal position, so a column reorder in the target schema doesn't change the hash. Make this configurable.
- Skip generated (virtual or stored) columns by default.

### 3.2 Order-independent aggregation (Percona `pt-table-checksum` style)

```sql
SELECT COUNT(*) AS cnt,
       COALESCE(BIT_XOR(CAST(CONV(LEFT(MD5(CONCAT_WS('#', <canonical cols>)), 16), 16, 10) AS UNSIGNED)), 0) AS crc
FROM `<table>`
[WHERE <pk> BETWEEN ? AND ?];
```

- Always compare **`COUNT(*)` and `crc` together**. `BIT_XOR` is safe because the primary key makes every row unique. For tables **without a primary key**, warn and fall back to a sorted full hash, ordering by `CAST(<all cols canonical> AS BINARY)`.
- No `ORDER BY` is needed for the aggregate.

### 3.3 Session pinning (done inside the script, never left to the defaults)

```sql
SET NAMES utf8mb4 COLLATE utf8mb4_bin;
SET time_zone = '+00:00';
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION WITH CONSISTENT SNAPSHOT;   -- one snapshot for all tables
```

### 3.4 Chunking and drill-down

- Split large tables into primary-key ranges, with a configurable chunk size (default 100k rows), and checksum each chunk.
- For every chunk that differs, run a **row-level diff**: fetch `pk + per-row MD5` on both sides and report the missing, extra and changed primary keys (capped, for example the first 100).
- For each changed row, report **which column** differs, along with `HEX()` of both values. This exposes double-encoding (for example `C3A9` vs `C383C2A9`).

### 3.5 Configuration file (`checksum_config.yml`)

```yaml
schema_map:                  # source schema -> target schema
  legacy_db: app_db
exclude_tables: [audit_log, sessions]
table_map:                   # renamed tables
  old_users: users
float_precision: 6
chunk_size: 100000
column_order: name           # name | ordinal
overrides:                   # intentional changes: normalize both sides the same way
  users.amount: "CAST(ROUND({col}, 2) AS CHAR)"
  orders.notes: skip         # documented exception, reported as SKIPPED, not PASS
```

### 3.6 Intentional charset/collation changes

- When a column moves from `latin1` to `utf8mb4` on purpose, the canonical form (`CONVERT(... USING utf8mb4)`) on **both** sides should still produce the same result if the conversion was correct. A mismatch then means a real problem, such as double-encoding.
- Optional `--semantic` mode: fetch the rows in Python, decode them to `str`, apply Unicode **NFC** normalization, and hash in Python. Use it for columns where SQL-side normalization isn't enough.

## 4. Deliverables

Put them under `db-migration/` unless the repo already has a better location (check first).

1. **`db-migration/checksum/db_checksum.py`**: a CLI.
   - `snapshot --role source|target --out before.json [--config checksum_config.yml] [--tables t1,t2] [--chunked]`
   - `compare before.json after.json [--summary-md summary.md]`: exits non-zero on any mismatch.
   - `diff --table T --chunk N`: row-level drill-down against both databases.
   - Connection details come **only** from environment variables (`SRC_DB_HOST`, `SRC_DB_PORT`, `SRC_DB_USER`, `SRC_DB_PASSWORD`, `SRC_DB_NAME`, and the same with `DST_`). Never from arguments, which appear in the logs.
   - Use `PyMySQL`. Pin dependencies in `requirements.txt`.
   - Output JSON: `{table: {count, crc, chunks: [{lo, hi, count, crc}], columns: [...], warnings: [...]}}` plus metadata (server version, charset, timestamp, tool version).
   - Use `logging`, never print secrets, and give each exit code a clear meaning (0 = match, 1 = mismatch, 2 = error).
2. **`db-migration/checksum/checksum_config.example.yml`**.
3. **`.github/workflows/db-migration.yml`**:
   - `on: workflow_dispatch`, with inputs for the tables and a dry-run flag.
   - `concurrency: db-migration` (never two runs at once).
   - `runs-on: [self-hosted, db-network]`, because GitHub-hosted runners can't reach private databases. Make the label configurable.
   - `environment: production`: environment secrets plus a required reviewer approval.
   - Steps: checkout → setup Python 3.12 → install dependencies → **snapshot the source** → run the existing migration script (placeholder `./db-migration/migrate.sh` if none exists) → **snapshot the target** → **compare** (append the Markdown table to `$GITHUB_STEP_SUMMARY`, use `pipefail`) → on failure run the drill-down for the failing chunks → **upload `before.json`, `after.json` and the diff reports as artifacts** (`if: always()`).
   - Pin action versions (`actions/checkout@v4`, `actions/setup-python@v5`, `actions/upload-artifact@v4`).
4. **`.github/workflows/checksum-tests.yml`**: CI for the tool itself, run on PRs that touch `db-migration/**`:
   - Use a `mysql:8.0` service container, plus a `mysql:5.7` job if feasible.
   - Run the tests with `pytest`.
5. **Tests (`db-migration/checksum/tests/`)** must prove these cases:
   - A `latin1_swedish_ci` table converted to `utf8mb4_0900_ai_ci` → **PASS**.
   - The same data with case-only duplicates (`'abc'`, `'ABC'`) under a `_ci` collation → **PASS**, and the result doesn't depend on order.
   - `utf8mb4_general_ci` → `utf8mb4_0900_ai_ci` (pad attribute change) with trailing-space values → **PASS**.
   - NULL vs `''` → **FAIL**, detected correctly.
   - A single changed character (`é` → `e`) → **FAIL**, and the drill-down names the PK and the column.
   - Double-encoded UTF-8 in `latin1` → **FAIL**, and the report shows both hex values.
   - A `TIMESTAMP` column with a different server `time_zone` on each side → **PASS**.
   - A column-order change in the target → **PASS** (with `column_order: name`).
   - A table without a primary key → a warning, and the fallback still works.
   - An `overrides` entry with `skip` → reported as SKIPPED.
6. **`db-migration/checksum/README.md`**: why this design exists (the causes in section 2), usage, configuration, how to read the report, how to add exceptions, and the limits.

## 5. Constraints

- **Never connect to real or production databases** during development. Use only local Docker or CI service containers.
- No secrets in code, config, logs or commit history. Use `.env.example` with placeholders only.
- No `CHECKSUM TABLE` and no `ORDER BY` on collated strings for hashing.
- Quote identifiers with backticks and escape them correctly. Table and column names come from `information_schema`, but must still be quoted safely.
- Minimal dependencies: `PyMySQL`, `PyYAML`, `pytest`.
- Keep the per-type normalization in **one function** so it's easy to audit and extend.
- Read-only on both databases: the checksum tool must never write.

## 6. Acceptance criteria

- [ ] All tests in section 4.5 pass locally (`docker compose up` + `pytest`) and in `checksum-tests.yml`.
- [ ] Snapshotting the same unchanged database twice gives identical JSON, apart from the metadata timestamp.
- [ ] Changing only collation or charset correctly (no data change) gives **PASS**.
- [ ] Any real data change gives **FAIL**, with the table, chunk, primary key and column identified.
- [ ] The workflow summary shows a per-table table of rows before, rows after and the result.
- [ ] The README explains how to run it locally and in Actions.

## 7. Before you start, ask me

1. Where are the existing migration scripts, and what language are they in? Should this tool live next to them?
2. Is it the same server or two different servers? Is replication available? If so, should `pt-table-checksum` be used in addition to this tool?
3. Which MySQL versions and charsets are the source and target?
4. Can the source be put in read-only mode during the checksum, or must it stay live (which requires the consistent-snapshot approach and a cut-over window)?
5. Roughly how large is the largest table, so the chunk size and runtime limits can be set? Note the 6-hour job limit on self-hosted runners and the job timeouts.
6. Do I already have a self-hosted runner, and what is its label?

## 8. Reference: industry tools, for comparison and not required

- **Percona Toolkit `pt-table-checksum` / `pt-table-sync`:** the standard choice when source and target are linked by replication.
- **PingCAP `sync-diff-inspector`:** chunked checksums, works for MySQL to MySQL.
- **`reladiff`** (a maintained fork of Datafold's `data-diff`): cross-database diff using hash bisection.
- **AWS DMS data validation:** if the migration runs on AWS.

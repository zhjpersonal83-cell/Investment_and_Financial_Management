# Monthly inflation maintenance

This tool merges the documented Trading Economics observations into the existing inflation table. The full source manifest and public-access limitations are in `data/SOURCE_NOTES.md`. Values are monthly headline **year-on-year percentages**, with reference months normalized to their 15th day. EUR uses headline HICP. Values are exact decimals with at most two decimal places; zero and negative inflation are valid.

The importer preserves existing observations outside the supplied source months. A source observation replaces a conflicting existing value for that same currency and month, and the dry-run report lists both values. If old rows disagree with one another for a month, the importer resolves them only when the supplied verified source explicitly covers that month and separately reports every old value. Conflicting old months outside source coverage stop the operation. Missing months are not invented or carried forward. Only adjacent known months with identical values are combined; `OBSERVED_THROUGH` preserves the final month of each combined interval. Existing rows without that field count only as observations of their own month.

Use Python 3.10+ with PyMySQL installed. Supply `IAFM_DB_URL` (for example `jdbc:mysql://localhost:3306/IAFM`) and `IAFM_DB_USER` through the maintenance environment, or specify `--database`, `--db-user`, and `--socket` explicitly. The password is requested securely when neither `IAFM_MIGRATION_PASSWORD` nor `IAFM_DB_PASSWORD` is set. `--prompt-password` always requests it interactively and ignores both password environment variables. Do not put credentials into the URL, command line, source code, or output files. The application runtime identity can retain DML-only privileges; migration uses a separate maintenance identity.

Run each command separately from this project directory. The default is read-only:

```sh
python3 tools/inflation/import_inflation.py
```

For the server maintenance identity and its separately installed PyMySQL library, the equivalent interactive dry run is:

```sh
env PYTHONPATH=/root/iafm-maintenance-libs python3 tools/inflation/import_inflation.py --db-user root --database IAFM --socket /var/run/mysqld/mysqld.sock --prompt-password
```

Replace the database name with the existing configured database if different. The password prompt is not echoed and contains no shell argument secret.

Review source coverage, new months, conflicting old/source values, and final row counts. Apply during the deployment maintenance window, after stopping the old application, so old code cannot concurrently modify inflation rows without the new lock:

```sh
python3 tools/inflation/import_inflation.py --apply --backup-dir /root/iafm-inflation-backups
```

`--apply` writes an exclusive, mode-0600 JSON backup of the inflation rows and their ID recorder before changes. It adds only the nullable `OBSERVED_THROUGH` column, unique `(VARIOUS_CURRENCIES, DATE)` index, and singleton `INFLATION_SERIES_LOCK` table. MySQL schema changes commit independently. After adding the column and lock table, inflation data replacement and ID sequence changes happen in one transaction, under row locks, with exact monthly read-back verification. Failures before data commit roll back that data transaction. Once the validated, deduplicated data commit, the unique index is created as a separate final DDL step. Keep the application stopped throughout this migration. A failure at that final step explicitly reports `data_committed_schema_incomplete`; the committed observations are not rolled back. Inspect the index error and retry before starting the application. Repeating the import against identical data makes no observation changes. For duplicate starts, the earliest original date and then smallest existing ID is retained.

Keep the private backup outside the web application and source archive. Restoring it is a separate, deliberate maintenance operation; do not copy backup records into source-control commits. It is not necessary to restore additive columns or lock tables to restore previous observation values.

New administrator CRUD operations use the same series lock and preserve these interval rules. Adding an already covered month with the same rate is idempotent. A different rate returns 409 and requires editing/deleting the existing interval first. Editing a multi-month interval changes its entire rate while keeping its dates and currency; moving those fields returns 400. Deleting a row deletes its full displayed interval. Adjacent identical known intervals merge automatically while gaps remain gaps.

Reproduce the source CSVs from captured public chart tooltips:

```sh
python3 tools/inflation/data/build_dataset.py
```

Run importer logic tests:

```sh
python3 -m unittest discover -s src/test/python -p test_inflation_import.py
```

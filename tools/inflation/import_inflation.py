#!/usr/bin/env python3
"""Merge sourced monthly CPI observations; read-only dry run unless --apply is explicit."""
import argparse
import csv
import datetime as dt
import getpass
import hashlib
import json
import os
from pathlib import Path
import sys
import uuid
from decimal import Decimal, InvalidOperation
from urllib.parse import urlparse

TABLE = "ANNUAL_INFLATION_RATES_IN_VARIOUS_CURRENCIES"
RECORDER = "ID_RECORDER_" + TABLE
MIN_ID = -2147483648
SOURCES = dict(zip("USD GBP CNY EUR JPY HKD AUD CAD CHF SGD".split(), "united-states united-kingdom china euro-area japan hong-kong australia canada switzerland singapore".split()))


def month(value):
    if isinstance(value, dt.datetime):
        value = value.date()
    if not isinstance(value, dt.date):
        value = dt.date.fromisoformat(str(value))
    return value.replace(day=15)


def next_month(value):
    return dt.date(value.year + (value.month == 12), value.month % 12 + 1, 15)


def exact_rate(value):
    try:
        parsed = Decimal(str(value))
        if not parsed.is_finite() or abs(parsed) > Decimal("999.99"):
            raise ValueError("Rate is outside DECIMAL(5,2) capacity")
        rounded = parsed.quantize(Decimal("0.01"))
        if parsed != rounded:
            raise ValueError("Rate has more than two decimal places")
        return rounded
    except InvalidOperation as error:
        raise ValueError("Invalid decimal inflation rate") from error


def load_csv(path):
    observations = {}
    with Path(path).open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            currency = row["currency"]
            expected_source = f"https://tradingeconomics.com/{SOURCES.get(currency, '')}/inflation-cpi"
            expected_measure = "headline_hicp_yoy" if currency == "EUR" else "headline_cpi_yoy"
            if currency not in SOURCES or row.get("source") != "Trading Economics" or row.get("source_url") != expected_source:
                raise ValueError("Only the documented Trading Economics currency sources are accepted")
            if row.get("frequency") != "monthly" or row.get("measure") != expected_measure:
                raise ValueError("Expected monthly headline year-on-year inflation")
            key = (currency, month(row["date"]))
            if key[1] > dt.date.today():
                raise ValueError("Future observations and forecasts are not accepted")
            value = exact_rate(row["rate"])
            if key in observations:
                raise ValueError(f"Duplicate source month: {currency} {key[1]}")
            observations[key] = value
    if not observations:
        raise ValueError("The source CSV is empty")
    return observations


def expand_existing(rows, source=None, conflicts=None):
    monthly = {}
    observed_rates = {}
    for row in rows:
        currency = row["VARIOUS_CURRENCIES"]
        if currency not in SOURCES:
            raise ValueError(f"Unrecognized existing currency {currency}; no rows were changed")
        start = month(row["DATE"])
        through = month(row.get("OBSERVED_THROUGH") or row["DATE"])
        if through < start or (through.year - start.year) > 200:
            raise ValueError("Invalid existing inflation interval")
        value = exact_rate(row["AIR_IN_VARIOUS_CURRENCIES"])
        cursor = start
        while cursor <= through:
            key = (currency, cursor)
            observed_rates.setdefault(key, set()).add(value)
            if len(observed_rates[key]) > 1:
                if source is None or key not in source:
                    raise ValueError(f"Conflicting existing intervals at {currency} {cursor}; no verified source covers this month")
                monthly[key] = source[key]
            else:
                monthly[key] = value
            cursor = next_month(cursor)
    if conflicts is not None:
        conflicts.extend({"currency": key[0], "date": str(key[1]), "old_rates": [str(value) for value in sorted(values)],
                          "source_rate": str(source[key])} for key, values in sorted(observed_rates.items()) if len(values) > 1)
    return monthly


def compact(monthly):
    spans = []
    for (currency, date), value in sorted(monthly.items()):
        previous = spans[-1] if spans else None
        if previous and previous["VARIOUS_CURRENCIES"] == currency and previous["AIR_IN_VARIOUS_CURRENCIES"] == value and next_month(previous["OBSERVED_THROUGH"]) == date:
            previous["OBSERVED_THROUGH"] = date
        else:
            spans.append({"VARIOUS_CURRENCIES": currency, "DATE": date, "OBSERVED_THROUGH": date, "AIR_IN_VARIOUS_CURRENCIES": value})
    return spans


def plan(rows, source):
    existing_conflicts = []
    existing = expand_existing(rows, source, existing_conflicts)
    conflicts = [{"currency": key[0], "date": str(key[1]), "old_rate": str(existing[key]), "source_rate": str(value)} for key, value in sorted(source.items()) if key in existing and existing[key] != value]
    combined = {**existing, **source}
    spans = compact(combined)
    coverage = []
    for currency in SOURCES:
        supplied = sorted(key[1] for key in source if key[0] == currency)
        merged = sorted(key[1] for key in combined if key[0] == currency)
        if merged:
            coverage.append({"currency": currency, "source_from": str(supplied[0]) if supplied else None,
                             "source_through": str(supplied[-1]) if supplied else None, "source_months": len(supplied),
                             "retained_existing_outside_source": sum(1 for key in existing if key[0] == currency and key not in source),
                             "final_from": str(merged[0]), "final_through": str(merged[-1]),
                             "final_spans": sum(s["VARIOUS_CURRENCIES"] == currency for s in spans)})
    report = {"existing_rows": len(rows), "existing_known_months": len(existing), "source_observations": len(source),
              "new_months": sum(key not in existing for key in source), "conflicts": conflicts,
              "existing_conflicts_resolved_by_source": existing_conflicts,
              "final_known_months": len(combined), "final_rows": len(spans),
              "collapsed_known_months": len(combined) - len(spans), "coverage": coverage}
    return spans, report


def schema(cursor):
    cursor.execute(f"SHOW COLUMNS FROM {TABLE}")
    columns = [row["Field"] for row in cursor.fetchall()]
    cursor.execute(f"SHOW INDEX FROM {TABLE}")
    indexes = cursor.fetchall()
    return columns, any(row["Key_name"].upper() == "UQ_INFLATION_CURRENCY_DATE" for row in indexes)


def read_rows(cursor, columns, locking=False):
    selected = "ID,AIR_IN_VARIOUS_CURRENCIES,VARIOUS_CURRENCIES,DATE"
    if "OBSERVED_THROUGH" in columns:
        selected += ",OBSERVED_THROUGH"
    cursor.execute(f"SELECT {selected} FROM {TABLE} ORDER BY VARIOUS_CURRENCIES,DATE,ID" + (" FOR UPDATE" if locking else ""))
    return list(cursor.fetchall())


def backup(path, rows, recorder, source_hash, columns):
    path.mkdir(parents=True, exist_ok=True)
    filename = path / ("inflation-before-" + dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8] + ".json")
    data = {"table": TABLE, "columns": columns, "rows": rows, "id_recorder": recorder, "source_csv_sha256": source_hash}
    descriptor = os.open(filename, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=2, default=str)
        handle.flush()
        os.fsync(handle.fileno())
    return filename


def apply(cursor, rows, spans, recorder):
    existing_ids = {}
    for row in sorted(rows, key=lambda value: (value["DATE"], value["ID"])):
        existing_ids.setdefault((row["VARIOUS_CURRENCIES"], month(row["DATE"])), row["ID"])
    sequence = [row["TABLE_ID"] for row in recorder if MIN_ID < row["TABLE_ID"] <= 0]
    if len(sequence) > 1:
        raise ValueError("Ambiguous existing ID sequence; no inflation data were changed")
    maximum = max([0] + [row["ID"] for row in rows] + [abs(row["TABLE_ID"]) for row in recorder if row["TABLE_ID"] != MIN_ID])
    assigned = []
    for span in spans:
        value = dict(span)
        key = (span["VARIOUS_CURRENCIES"], span["DATE"])
        if key in existing_ids:
            value["ID"] = existing_ids[key]
        else:
            maximum += 1
            if maximum > 2147483647:
                raise ValueError("The integer ID range is exhausted")
            value["ID"] = maximum
        assigned.append(value)
    canonical_old = [{**row, "DATE": month(row["DATE"]), "OBSERVED_THROUGH": month(row.get("OBSERVED_THROUGH") or row["DATE"]), "AIR_IN_VARIOUS_CURRENCIES": exact_rate(row["AIR_IN_VARIOUS_CURRENCIES"])} for row in rows]
    if canonical_old == assigned and all(row["DATE"] == month(row["DATE"]) and row.get("OBSERVED_THROUGH") is not None for row in rows):
        return False
    cursor.execute(f"DELETE FROM {TABLE}")
    cursor.executemany(f"INSERT INTO {TABLE} (ID,AIR_IN_VARIOUS_CURRENCIES,VARIOUS_CURRENCIES,DATE,OBSERVED_THROUGH) VALUES (%s,%s,%s,%s,%s)",
                       [(row["ID"], row["AIR_IN_VARIOUS_CURRENCIES"], row["VARIOUS_CURRENCIES"], row["DATE"], row["OBSERVED_THROUGH"]) for row in assigned])
    if sequence:
        cursor.execute(f"UPDATE {RECORDER} SET TABLE_ID=%s WHERE TABLE_ID=%s", (-maximum, sequence[0]))
    else:
        cursor.execute(f"INSERT INTO {RECORDER} (TABLE_ID) VALUES (%s)", (-maximum,))
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--csv", type=Path, default=Path(__file__).parent / "data" / "inflation_monthly_raw.csv")
    parser.add_argument("--apply", action="store_true", help="Back up, migrate inflation-only schema and apply one transaction")
    parser.add_argument("--backup-dir", type=Path, default=Path.cwd() / "inflation-backups")
    parser.add_argument("--db-user", default=os.environ.get("IAFM_DB_USER", "root"))
    parser.add_argument("--database", help="Database name; otherwise use IAFM_DB_URL or IAFM")
    parser.add_argument("--socket", help="Connect using this local MySQL Unix socket")
    parser.add_argument("--prompt-password", action="store_true", help="Prompt securely even if an environment password is set")
    args = parser.parse_args()
    source = load_csv(args.csv)
    source_hash = hashlib.sha256(args.csv.read_bytes()).hexdigest()
    import pymysql
    url = urlparse(os.environ.get("IAFM_DB_URL", "jdbc:mysql://localhost:3306/IAFM").removeprefix("jdbc:"))
    if url.scheme != "mysql" or url.username or url.password or not args.db_user:
        raise ValueError("Provide a MySQL URL without credentials and a maintenance database user")
    password = None if args.prompt_password else os.environ.get("IAFM_MIGRATION_PASSWORD", os.environ.get("IAFM_DB_PASSWORD"))
    if password is None:
        password = getpass.getpass("Database password: ")
    connection = pymysql.connect(host=url.hostname or "localhost", port=url.port or 3306, database=args.database or url.path.lstrip("/") or "IAFM",
                                 unix_socket=args.socket, user=args.db_user, password=password,
                                 charset="utf8mb4", autocommit=False, cursorclass=pymysql.cursors.DictCursor)
    data_committed = False
    backup_file = None
    try:
        with connection.cursor() as cursor:
            columns, has_unique = schema(cursor)
            rows = read_rows(cursor, columns)
            spans, report = plan(rows, source)
            report.update({"mode": "apply" if args.apply else "dry-run", "source_csv_sha256": source_hash,
                           "schema_add_observed_through": "OBSERVED_THROUGH" not in columns, "schema_add_unique_index": not has_unique})
            print(json.dumps(report, indent=2))
            if not args.apply:
                connection.rollback()
                return
            cursor.execute(f"SELECT TABLE_ID FROM {RECORDER} ORDER BY TABLE_ID")
            recorder = list(cursor.fetchall())
            backup_file = backup(args.backup_dir, rows, recorder, source_hash, columns)
            connection.commit()
            # MySQL DDL commits implicitly. Make only additive schema changes after the backup,
            # before beginning the transaction that replaces any inflation observations.
            if "OBSERVED_THROUGH" not in columns:
                cursor.execute(f"ALTER TABLE {TABLE} ADD COLUMN OBSERVED_THROUGH DATE NULL")
                columns.append("OBSERVED_THROUGH")
            cursor.execute("CREATE TABLE IF NOT EXISTS INFLATION_SERIES_LOCK (ID INT PRIMARY KEY) ENGINE=InnoDB")
            cursor.execute("INSERT INTO INFLATION_SERIES_LOCK (ID) VALUES (1) ON DUPLICATE KEY UPDATE ID=ID")
            connection.commit()
            connection.begin()
            cursor.execute("SELECT ID FROM INFLATION_SERIES_LOCK WHERE ID=1 FOR UPDATE")
            if not cursor.fetchone():
                raise ValueError("Inflation lock marker is unavailable")
            current = read_rows(cursor, columns, locking=True)
            cursor.execute(f"INSERT INTO {RECORDER} (TABLE_ID) VALUES (%s) ON DUPLICATE KEY UPDATE TABLE_ID=TABLE_ID", (MIN_ID,))
            cursor.execute(f"SELECT TABLE_ID FROM {RECORDER} ORDER BY TABLE_ID FOR UPDATE")
            locked_recorder = list(cursor.fetchall())
            if current != rows or locked_recorder != recorder:
                backup_file = backup(args.backup_dir, current, locked_recorder, source_hash, columns)
            spans, fresh_report = plan(current, source)
            changed = apply(cursor, current, spans, locked_recorder)
            verified = read_rows(cursor, columns)
            if expand_existing(verified) != {**expand_existing(current, source), **source}:
                raise ValueError("Post-write monthly read-back differs; transaction will be rolled back")
            connection.commit()
            data_committed = True
            # Old rows may contain duplicate dates. First commit the validated, deduplicated
            # data, then add the unique index as separate DDL while the app is stopped.
            # A failed DDL cannot roll back the already committed observations.
            if not has_unique:
                cursor.execute(f"CREATE UNIQUE INDEX UQ_INFLATION_CURRENCY_DATE ON {TABLE} (VARIOUS_CURRENCIES,DATE)")
            print(json.dumps({"status": "committed" if changed else "unchanged", "rows": len(verified), "backup": str(backup_file),
                              "conflicts_applied": len(fresh_report["conflicts"]), "existing_conflicts_resolved": len(fresh_report["existing_conflicts_resolved_by_source"])}, indent=2))
    except Exception:
        connection.rollback()
        if data_committed:
            print(json.dumps({"status": "data_committed_schema_incomplete", "backup": str(backup_file),
                              "action": "Keep the application stopped; inspect the index error and retry. Committed data were not rolled back."}), file=sys.stderr)
        raise
    finally:
        connection.close()


if __name__ == "__main__":
    main()

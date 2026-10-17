import csv
import datetime as dt
import importlib.util
from decimal import Decimal
from pathlib import Path
import tempfile
import unittest

PROJECT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("inflation_import", PROJECT / "tools/inflation/import_inflation.py")
subject = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(subject)


def row(identifier, currency, date, rate, through=None):
    return {"ID": identifier, "VARIOUS_CURRENCIES": currency, "DATE": dt.date.fromisoformat(date),
            "AIR_IN_VARIOUS_CURRENCIES": Decimal(rate),
            "OBSERVED_THROUGH": dt.date.fromisoformat(through) if through else None}


class FakeCursor:
    def __init__(self):
        self.calls = []

    def execute(self, query, args=None):
        self.calls.append((query, args))

    def executemany(self, query, args):
        self.calls.append((query, args))


class InflationImportTest(unittest.TestCase):
    def test_exact_rates_and_month_dates(self):
        self.assertEqual(subject.exact_rate("-0.20"), Decimal("-0.20"))
        self.assertEqual(subject.exact_rate("0"), Decimal("0.00"))
        self.assertEqual(subject.month("2020-02-29"), dt.date(2020, 2, 15))
        for invalid in ("1.005", "NaN", "Infinity", "1000", "-1000"):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                subject.exact_rate(invalid)

    def test_only_adjacent_known_months_collapse(self):
        months = {("GBP", dt.date(2020, value, 15)): Decimal("2.00") for value in (1, 2, 4)}
        months[("CNY", dt.date(2020, 2, 15))] = Decimal("2.00")
        spans = subject.compact(months)
        gbp = [span for span in spans if span["VARIOUS_CURRENCIES"] == "GBP"]
        self.assertEqual(len(gbp), 2)
        self.assertEqual(gbp[0]["DATE"], dt.date(2020, 1, 15))
        self.assertEqual(gbp[0]["OBSERVED_THROUGH"], dt.date(2020, 2, 15))
        self.assertEqual(subject.expand_existing(spans), months)

    def test_legacy_rows_do_not_imply_missing_months(self):
        original = [row(1, "CNY", "2016-08-15", "1.30"), row(2, "CNY", "2016-10-15", "2.10", "2016-11-15")]
        expanded = subject.expand_existing(original)
        self.assertEqual(len(expanded), 3)
        self.assertNotIn(("CNY", dt.date(2016, 9, 15)), expanded)

    def test_source_conflicts_are_reported_and_older_rows_preserved(self):
        original = [row(1, "CNY", "2016-08-15", "1.30"), row(2, "CNY", "2016-10-15", "2.10")]
        source = {("CNY", dt.date(2016, 10, 15)): Decimal("2.00"), ("CNY", dt.date(2016, 11, 15)): Decimal("2.00")}
        spans, report = subject.plan(original, source)
        self.assertEqual(len(spans), 2)
        self.assertEqual(report["new_months"], 1)
        self.assertEqual(report["conflicts"], [{"currency": "CNY", "date": "2016-10-15", "old_rate": "2.10", "source_rate": "2.00"}])
        self.assertEqual(report["coverage"][0]["retained_existing_outside_source"], 1)
        self.assertEqual(subject.expand_existing(spans)[("CNY", dt.date(2016, 8, 15))], Decimal("1.30"))
        repeated, repeated_report = subject.plan(spans, source)
        self.assertEqual(repeated, spans)
        self.assertEqual(repeated_report["conflicts"], [])
        self.assertEqual(repeated_report["new_months"], 0)

    def test_apply_is_noop_when_spans_already_match(self):
        original = [row(10, "GBP", "2020-01-15", "2.00", "2020-02-15")]
        spans = subject.compact(subject.expand_existing(original))
        cursor = FakeCursor()
        self.assertFalse(subject.apply(cursor, original, spans, [{"TABLE_ID": -10}, {"TABLE_ID": subject.MIN_ID}]))
        self.assertEqual(cursor.calls, [])

    def test_apply_preserves_start_id_and_advances_sequence(self):
        original = [row(10, "GBP", "2020-01-15", "2.00")]
        spans, _ = subject.plan(original, {("GBP", dt.date(2020, 2, 15)): Decimal("2.00"), ("USD", dt.date(2020, 1, 15)): Decimal("1.00")})
        cursor = FakeCursor()
        self.assertTrue(subject.apply(cursor, original, spans, [{"TABLE_ID": -12}, {"TABLE_ID": subject.MIN_ID}]))
        inserted = cursor.calls[1][1]
        self.assertEqual([value[0] for value in inserted], [10, 13])
        self.assertEqual(inserted[0][4], dt.date(2020, 2, 15))
        self.assertEqual(cursor.calls[2][1], (-13, -12))

    def test_verified_source_resolves_actual_legacy_duplicate_and_preserves_smallest_id(self):
        original = [row(102, "CNY", "2023-12-15", "-0.30"), row(122, "CNY", "2023-12-15", "0.10")]
        source = {("CNY", dt.date(2023, 12, 15)): Decimal("-0.30")}
        spans, report = subject.plan(original, source)
        self.assertEqual(report["existing_conflicts_resolved_by_source"], [{"currency": "CNY", "date": "2023-12-15", "old_rates": ["-0.30", "0.10"], "source_rate": "-0.30"}])
        self.assertEqual(subject.expand_existing(spans), source)
        cursor = FakeCursor()
        self.assertTrue(subject.apply(cursor, original, spans, [{"TABLE_ID": -122}]))
        self.assertEqual(cursor.calls[1][1], [(102, Decimal("-0.30"), "CNY", dt.date(2023, 12, 15), dt.date(2023, 12, 15))])

    def test_uncovered_existing_conflict_is_never_silently_resolved(self):
        original = [row(102, "CNY", "2023-12-01", "-0.30"), row(122, "CNY", "2023-12-15", "0.10")]
        with self.assertRaisesRegex(ValueError, "no verified source covers this month"):
            subject.plan(original, {("CNY", dt.date(2024, 1, 15)): Decimal("-0.80")})
        with self.assertRaisesRegex(ValueError, "no verified source covers this month"):
            subject.expand_existing(original)

    def test_csv_provenance_and_duplicate_months_validated(self):
        valid = {"currency": "GBP", "date": "2020-01-15", "rate": "1.80", "source": "Trading Economics",
                 "source_url": "https://tradingeconomics.com/united-kingdom/inflation-cpi", "frequency": "monthly", "measure": "headline_cpi_yoy"}
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "source.csv"
            for values in ([valid, dict(valid)], [{**valid, "measure": "month_on_month"}], [{**valid, "source_url": "https://example.com/"}], [{**valid, "date": "2099-01-15"}]):
                with path.open("w", newline="") as handle:
                    writer = csv.DictWriter(handle, fieldnames=valid)
                    writer.writeheader()
                    writer.writerows(values)
                with self.assertRaises(ValueError):
                    subject.load_csv(path)

    def test_bundled_public_data_has_expected_coverage_and_gap(self):
        source = subject.load_csv(PROJECT / "tools/inflation/data/inflation_monthly_raw.csv")
        self.assertEqual(len(source), 1096)
        self.assertEqual(len(subject.compact(source)), 932)
        self.assertEqual({currency for currency, date in source}, set(subject.SOURCES))
        self.assertNotIn(("USD", dt.date(2025, 10, 15)), source)
        aud = sorted(date for currency, date in source if currency == "AUD")
        self.assertEqual((len(aud), aud[0]), (17, dt.date(2025, 4, 15)))


if __name__ == "__main__":
    unittest.main()

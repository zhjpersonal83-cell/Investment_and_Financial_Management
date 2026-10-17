# IAFM release — 2026-10-17

Application: https://iafm.zhongjiehuang.com/ . The ZIP, GitHub repository and top-level folder retain the full name Investment_and_Financial_Management. The deployable is dist/Investment_and_Financial_Management.war.

## Architecture and production configuration

The Java Servlet MVC layout and original financial tables remain. New journal and inflation interval structures supplement existing records. Build with Java 21 and mvn clean package; fixture tests need no production credentials.

The Tomcat context remains /Investment_and_Financial_Management. Nginx maps the subdomain root to this context, rewrites its cookie path to /, and redirects the old www context URL. It must overwrite Host, X-Real-IP and X-Forwarded-Proto. The application trusts forwarding headers only from loopback.

Configure IAFM_DB_URL, IAFM_DB_USER, IAFM_DB_PASSWORD and IAFM_ADMIN_USER_IDS outside source. The production identity has SELECT, INSERT, UPDATE and DELETE on IAFM only. Set IAFM_SCHEMA_AUTO_INIT=false after applying maintenance migrations; startup then verifies schema without DDL. The default true supports development/bootstrap.

Preserve the private app context descriptor when replacing the WAR. The provider key is configured through IAFM_EXCHANGE_RATE_API_KEY or the context Environment entry iafm/exchangeRateApiKey. Its CookieProcessor uses sameSiteCookies="lax"; the app declares HttpOnly/Secure cookies and a ten-minute idle timeout. Preserve the existing Tomcat ObjectStreamClass cleanup compatibility setting.

For this release, files were staged before one stop/start maintenance window. Database backup and migrations precede installing the new IAFM and ROOT WARs, restoring the private context, and starting Tomcat. Replace only those deployment directories and their scoped work caches. The extra WishGlow deployment was subsequently removed at the owner's request by hot undeploy. Marginloom runs as an independent service and its application code was unchanged.

## Currency, inflation and frontend

Reports and inflation support USD, GBP, CNY, EUR, JPY, HKD, AUD, CAD, CHF and SGD. Backend conversions use the existing formulas and shared daily exchange-rate snapshot. Presentation uses two decimals while calculations retain precision. Preserve the cache across deployments. Page visits never call the provider; there is at most one provider attempt per 24 hours, including failures and reloads.

Sign-in and workspace navigation link to the exchange-rate page. The frontend removes expensive backdrop blur and long animation effects, installs activity listeners once, and does not poll or rebuild content on scroll.

Inflation mutation requires an explicitly configured administrator on the backend. Other users cannot bypass hidden controls with direct API requests. Dates normalize to the 15th. Adjacent equal observations retain the earliest row; OBSERVED_THROUGH preserves their known interval. Missing months are never inferred. Editing a span changes its rate over that interval, moving a multi-month span is rejected, and deletion removes the whole displayed span. Unique currency/date constraints, serialization and transaction rollback protect writes.

tools/inflation/README.md contains public Trading Economics provenance, reproducible evidence and import commands. The retrieved series has 1,096 observations and 932 compressed intervals. USD 2025-10 is unavailable; Australia's public monthly series begins 2025-04. Other visible histories begin 2016-09 or 2016-10. Existing earlier observations are retained; subscription-only history and missing monthly values are not invented.

## Accounting and security

Income and expense writes create balanced journals. Verified internal transfers have two asset legs, are excluded from income/expense summaries and appear separately. Sorting uses SQL allowlists. Cross-currency transfers preserve actual amounts and balance in the outgoing currency; no historical market rate is invented. Fees remain external expense. Single-leg edits of paired transfers are rejected; deletion reverses both legs atomically. Renaming an account updates the owner's asset journal names in the same transaction.

scripts/migrate_ledger.py defaults to dry-run. Apply requires a private evidence plan, exact owner identity and exclusive backup. It verifies unchanged transaction counts, unchanged account balances and balanced journals. Ambiguous pair candidates retain their original classification.

scripts/migrate_passwords.py upgrades legacy plaintext database passwords to the existing PBKDF2 format without changing users' passwords; it defaults to dry-run and requires a private backup to apply. Login also retains successful legacy migration, equivalent failed-login KDF cost, bounded IP throttling, strict origins and session renewal. Security headers restrict framing/content loading. JSON-java and Connector/J dependencies were updated.

Private bank files are ignored by Git. Account credentials are supplied through external IAFM_PRIVATE_ACCOUNTS_FILE, never embedded in Python source or distributed archives. See src/main/Python/PRIVACY.md. A private-data ZIP must not be uploaded as a public source release.

## Verification

Java 21 clean package: 70 tests passed. Coverage includes money and cross-rate calculations, owner isolation, paired journals, account rename/delete, inflation intervals/rollback/concurrency, password migration and failed-login KDF work, origins, throttling and daily-rate persistence. Python importers and the Chrome frontend have additional regression checks. Final results and artifact hashes are in VERIFICATION.json.

These are scoped application hardening measures, not an independent penetration-test certification. Shared-host upgrades and ongoing monitoring remain separate operational maintenance.

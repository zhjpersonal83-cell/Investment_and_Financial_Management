# Investment and Financial Management

Investment and Financial Management (IAFM) is a Java web application for personal financial records, assets, transfers and reports.

- [Live application](https://iafm.zhongjiehuang.com/)
- [Project portfolio](https://www.zhongjiehuang.com/)
- [Deployment notes](DEPLOYMENT_UPDATE_2026-10-17.md)
- [Implementation and configuration](README_FIXES_ZH.md)
- [Verification record](VERIFICATION.json)

The full project name remains `Investment_and_Financial_Management`; IAFM is its short name.

## Build

Use Java 21 and Maven. Run each command separately from this directory:

```sh
mvn test
mvn package
```

Configure the database and exchange-rate provider through the documented environment or server configuration. Keep credentials outside this repository. Preserve the existing application database when deploying an update.

## Data privacy

This repository contains application code, tests and generic import tools. Private bank statements, account credentials, import checkpoints and runtime database files are excluded. Importers read owner-supplied local inputs; see [Python import privacy notes](src/main/Python/PRIVACY.md).

The public repository starts from a reviewed source snapshot. Earlier private development history and its historical binaries are kept separately in a private archive repository.

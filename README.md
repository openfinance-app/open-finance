# Open-Finance — NG Personal Finance Manager

> **Local-first, privacy-focused personal finance application.**  
> Consolidates your assets, liabilities, and transactions in one secured dashboard.

![Open Finance Dashboard](docs/wiki/screenshots/hero.png)

[![Backend CI](https://github.com/albilu/open-finance/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/albilu/open-finance/actions/workflows/backend-ci.yml)
[![Frontend CI](https://github.com/albilu/open-finance/actions/workflows/frontend-ci.yml/badge.svg)](https://github.com/albilu/open-finance/actions/workflows/frontend-ci.yml)
[![Publish Docker Image](https://github.com/albilu/open-finance/actions/workflows/docker-publish.yml/badge.svg)](https://github.com/albilu/open-finance/actions/workflows/docker-publish.yml)
[![Release](https://img.shields.io/github/v/release/albilu/open-finance)](https://github.com/albilu/open-finance/releases/latest)
[![License: ELv2](https://img.shields.io/badge/License-Elastic_v2-blue.svg)](LICENSE)

---

## Quick Start — Docker (Recommended)

Requires [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/).

```bash
git clone https://github.com/albilu/open-finance.git
cd open-finance

cp .env.example .env

# Generate a secure JWT signing secret (required, min 32 chars)
sed -i "s/JWT_SECRET=REPLACE_WITH_A_LONG_RANDOM_SECRET_MIN_32_CHARS/JWT_SECRET=$(openssl rand -base64 48)/" .env

docker compose up -d
# Open http://localhost:8080
```

_Bug reports and feature requests → [GitHub Issues](https://github.com/open-finance/open-finance/issues)_

---

## Deployment Options

|                   | Self-Hosted    | Cloud           |
| ----------------- | -------------- | --------------- |
| **Cost**          | Free           | Subscription    |
| **Setup**         | Docker Compose | None            |
| **Data location** | Your machine   | Managed hosting |
| **Updates**       | Manual         | Automatic       |

## Development

The recommended setup uses the pre-configured **DevContainer** — includes Java 21, Maven, Node.js, SQLite, and all VS Code extensions, with zero manual installation.

**Requirements**: Docker + VS Code + [Dev Containers extension](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers)

```bash
git clone https://github.com/albilu/open-finance.git
code open-finance
# F1 → "Dev Containers: Reopen in Container"

mvn spring-boot:run   # Backend
npm run dev           # Frontend

# Backend (from repo root)
mvn test
mvn clean test jacoco:report   # with coverage

# Frontend (from openfinance-ui/)
npm test
npm run type-check && npm run lint
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for architecture, API reference, coding standards, and how to submit changes.  
See [docs/SECURITY.md](docs/SECURITY.md) for encryption, authentication, and responsible disclosure.

---

## PFA Landscape

| Feature                   | Open-Finance | Finary | Maybe/Sure | Wealthfolio | GnuCash | HomeBank | MMEX | KMyMoney | Skrooge |
| ------------------------- | ------------ | ------ | ---------- | ----------- | ------- | -------- | ---- | -------- | ------- |
| **Modern Interface**      | ✅           | ✅     | ✅         | ✅          | ⚠️      | ⚠️       | ⚠️   | ⚠️       | ⚠️      |
| **Basic Finance Mgmt**    | ✅           | ✅     | ✅         | ⚠️          | ✅      | ✅       | ✅   | ✅       | ✅      |
| **Rich Dashboards**       | ✅           | ✅     | ✅         | ✅          | ❌      | ⚠️       | ⚠️   | ⚠️       | ⚠️      |
| **QIF/OFX Import**        | ✅           | ❌     | ❌         | ⚠️          | ✅      | ✅       | ⚠️   | ✅       | ✅      |
| **Real Estate Mgmt**      | ✅           | ✅     | ⚠️         | ⚠️          | ⚠️      | ❌       | ❌   | ⚠️       | ❌      |
| **File Attachments**      | ✅           | ❌     | ❌         | ❌          | ✅      | ⚠️       | ✅   | ✅       | ✅      |
| **Auto Categorization**   | ✅           | ✅     | ✅         | ❌          | ⚠️      | ⚠️       | ⚠️   | ⚠️       | ⚠️      |
| **Undo/Redo/Backup**      | ✅           | ⚠️     | ⚠️         | ⚠️          | ✅      | ⚠️       | ⚠️   | ✅       | ✅      |
| **Financial Planning**    | ✅           | ✅     | ✅         | ✅          | ⚠️      | ⚠️       | ⚠️   | ⚠️       | ⚠️      |
| **Financial News**        | ✅           | ❌     | ❌         | ❌          | ❌      | ❌       | ❌   | ❌       | ❌      |
| **Multi-Currency**        | ✅           | ✅     | ✅         | ✅          | ✅      | ✅       | ✅   | ✅       | ✅      |
| **AI Assistant**          | ✅           | ❌     | ✅         | ✅          | ❌      | ❌       | ❌   | ❌       | ❌      |
| **Data Security/Privacy** | ✅           | ❌     | ⚠️         | ✅          | ✅      | ✅       | ✅   | ✅       | ✅      |
| **FOSS**                  | ✅           | ❌     | ✅         | ✅          | ✅      | ✅       | ✅   | ✅       | ✅      |
| **Multilingual (EN/FR)**  | ✅           | ✅     | ✅         | ❌          | ✅      | ✅       | ✅   | ✅       | ✅      |

✅ Full support · ⚠️ Partial · ❌ Not supported

Full feature User Guide → [Wiki](docs/wiki/HOME.md)

---

## License

[Elastic License v2 (ELv2)](LICENSE) — free to self-host and modify.  
**Offering this software as a managed/hosted service to third parties requires a commercial license.**

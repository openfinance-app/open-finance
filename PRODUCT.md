# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Individuals managing their personal wealth who want a consolidated view of accounts,
assets, liabilities, budgets, and real estate in one place. A significant share are
privacy-conscious self-hosters running the app via Docker on their own hardware
(inferred from README positioning, docker-compose quickstart, and client-side
encryption design). Primary job: understand net worth and cash flow at a glance, then
drill into accounts, transactions, budgets, and investments. Locales: English and French.

## Product Purpose

Open Finance is a local-first, privacy-focused personal finance manager (README:
"Consolidates your assets, liabilities, and transactions in one secured dashboard").
It exists so users own their financial data end-to-end: self-hosted backend, encrypted
sensitive fields, session-scoped decryption keys. Success means a user opens the
dashboard and immediately knows where they stand — net worth, cash flow, budget
health, portfolio movement — and can act (add transaction, import, adjust budget)
in seconds.

## Positioning

The open-source, self-hosted alternative to hosted PFAs (Finary, Monarch): Finary-grade
dashboards and multi-currency aggregation, plus things hosted tools don't offer —
QIF/OFX/CSV/JSON import, real-estate management, file attachments, undo/redo with
full audit history, financial news, and client-side encryption where the server never
sees plaintext amounts. Licensed Elastic v2.

## Operating Context

Daily-driver web app used on desktop and mobile browsers; sessions are authenticated
(JWT) and amounts can be masked via a global privacy toggle. Users import bank files,
manage recurring transactions and auto-categorization rules, track portfolios with
market-data refresh, run financial calculators (FIRE, compound interest, loans,
buy-vs-rent), and back up / restore their data. Demo account exists (demo/demo123).

## Capabilities and Constraints

- Multi-currency accounts, institutions, transactions (income/expense/transfer,
  splits, tags, attachments), recurring transactions, rules engine.
- Assets/portfolio with market data, liabilities with amortization, real estate.
- Budgets with alerts and progress; dashboard with customizable card grid
  (react-grid-layout, layouts persisted in localStorage — card ids must stay stable).
- Global search, AI assistant (floating chat + generated insights), RSS financial news.
- Import wizard (QIF/OFX/CSV/JSON), backup/restore, session history with undo/redo.
- i18n en/fr; all user-facing strings must exist in both locales.
- Stack fixed: React 19 + TS, Vite, Tailwind v4 (CSS-first `@theme`), Radix, Recharts,
  TanStack Query. No new heavy deps without cause.
- Money displayed in base currency with optional secondary conversion; tabular
  numerals for amounts are an established trait.

## Brand Commitments

- Name: **Open Finance**; existing wordmark/logo (amber chart icon) may evolve visually
  but the name stays.
- Product truth, routes, features, i18n keys, and demo data are preserved — this is a
  visual redesign, not a feature change.
- Privacy affordances (amount-masking eye toggle, encryption session) must remain
  discoverable.

## Evidence on Hand

Real demo dataset (accounts, transactions, budgets, assets) served at
http://localhost:3000 with demo/demo123. Wiki docs in `docs/wiki/` with screenshots.
No testimonials, press, or commercial claims exist — none may be fabricated.
"Community" and "Premium" routes are declared placeholders ("coming soon").

## Product Principles

1. **Data ownership is the product** — every design decision reinforces privacy,
   local-first control, and trust.
2. **Clarity before decoration** — this is an Operate surface; scanability of amounts,
   states, and tasks outranks expression.
3. **Numbers are the hero** — amounts, deltas, and trends carry the interface; chrome
   recedes.
4. **One system, every surface** — dashboard, lists, dialogs, and tools share one
   visual grammar.
5. **Bilingual by default** — nothing ships in one locale only.

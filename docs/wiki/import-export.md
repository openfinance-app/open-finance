# Import & Export

← [Wiki Home](HOME.md)

---

## Overview

Open-Finance can import transaction history from the most common bank export formats and export all your data in multiple formats for external analysis or migration.

![Import Transactions Interface](screenshots/import.png)

---

## Supported Import Formats

| Format | Extension | Notes                                      |
| ------ | --------- | ------------------------------------------ |
| OFX    | `.ofx`    | Open Financial Exchange — most banks       |
| QFX    | `.qfx`    | Quicken variant of OFX                     |
| QIF    | `.qif`    | Quicken Interchange Format — older format  |
| CSV    | `.csv`    | Comma-separated values — see headers below |
| JSON   | `.json`   | JSON array of transaction objects          |

CSV files must include a header row. Supported column names: `date`, `amount`, `payee`, `memo`, `category`, `type`, `referencenumber`. Rows with missing `date` or `amount` are skipped.

---

## Import Process

### Step 1 — Upload Your File

Go to **Import → Import Transactions**. Select your account and upload your bank export file (OFX, QFX, or QIF). Open-Finance parses the file and shows a preview of the transactions it found.

### Step 2 — Review the Preview

The preview table shows each transaction with:

- Parsed date, payee, amount, and memo
- Auto-detected category (via [Transaction Rules](transaction-rules.md))
- A **Duplicate** flag if the transaction appears to already exist in your account
- The suggested account name from the file

You can adjust categories, exclude specific rows, or override duplicate flags before continuing.

### Step 3 — Confirm

Click **Confirm Import** to save the selected transactions to your account. Only the rows you approve are imported.

---

## Duplicate Detection

The importer automatically flags potential duplicates by matching on:

- Same account
- Same date (± 1 day tolerance)
- Same amount
- Matching bank reference ID

Flagged duplicates are highlighted in the preview. You can choose to skip them or import them anyway.

---

## Limitations

### Multi-currency accounts &amp; QIF limitations

Some tools (notably Skrooge) hold **multiple units in a single account** — a foreign currency
(e.g. XOF/CFA), a crypto asset (BTC, USDT), or a security (shares). Open-Finance keeps one currency
per account, so these are collapsed to your **home currency** on import.

For QIF specifically, Skrooge repurposes the investment fields to encode this: `Y` is the unit,
`Q` is the native quantity, and `I` is the per-unit price in your home currency. Open-Finance
values every such line as `Q × I`, so the imported account matches the balance Skrooge itself
displays. Opening balances (Skrooge's leading no-payee record) are counted the same way, whether
they are home-currency or foreign.

**Known limitation — same-currency transfer direction (QIF only).** QIF has no concept of
currency and stores every `Q` quantity **unsigned**. Open-Finance recovers a transfer's direction
from the signed side of the pair:

- A transfer between a foreign account and a home-currency account imports correctly, because the
  home-currency leg carries a signed amount.
- A transfer between **two same-foreign-currency accounts** (e.g. CFA → CFA) has two identical
  unsigned legs, so its direction cannot be recovered from the file. Such a transfer may be
  imported reversed, which flips the sign on **both** affected account balances.

Net worth is unaffected (the two legs cancel out); only the per-account split can be wrong. For
exact per-account fidelity on multi-currency data, prefer Skrooge's **JSON/SQLite** export, which
preserves transaction signs.

---

## Related Pages

- [Transactions](transactions.md)
- [Transaction Rules](transaction-rules.md)
- [Accounts](accounts.md)
- [Backup & Restore](backup-restore.md)

# Import & Export

← [Wiki Home](HOME.md)

---

## Overview

Open-Finance can import transaction history from the most common bank export formats and export all your data in multiple formats for external analysis or migration.

![Import Transactions Interface](screenshots/import.png)

---

## Supported Import Formats

| Format | Extension | Notes                                     |
| ------ | --------- | ----------------------------------------- |
| OFX    | `.ofx`    | Open Financial Exchange — most banks      |
| QFX    | `.qfx`    | Quicken variant of OFX                    |
| QIF    | `.qif`    | Quicken Interchange Format — older format |
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

## Related Pages

- [Transactions](transactions.md)
- [Transaction Rules](transaction-rules.md)
- [Accounts](accounts.md)
- [Backup & Restore](backup-restore.md)

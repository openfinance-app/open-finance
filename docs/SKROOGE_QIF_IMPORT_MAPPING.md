# Skrooge QIF → Open-Finance Import Mapping

## Goal

This document defines the current, real-world mapping from a Skrooge QIF export into Open-Finance.
It is based on the actual sample file `skrooge_exports/my_export.qif` and the current Open-Finance QIF parser / import-confirmation flow.

**Important:** Skrooge JSON remains the highest-fidelity migration path. QIF can still import accounts, transactions, transfers, and split transactions, but it loses some metadata that the JSON importer preserves.

---

## Fidelity summary

| Area                 | Current QIF fidelity       | Notes                                                                                                                                         |
| -------------------- | -------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| Accounts             | Partial                    | Account routing works from `!Account` names, but account type and rich account metadata are not preserved in the generic QIF flow             |
| Categories           | Partial                    | Transaction and split category strings are preserved, but standalone `!Type:Cat` definitions are not imported as first-class category records |
| Transactions         | Good                       | Date, amount, payee, memo, reference, and account context are parsed reliably                                                                 |
| Transfers            | Good with bracketed target | Skrooge syntax like `L[00000463202]/Transfert` now maps to linked transfers; unbracketed `LTransfert` remains ambiguous                       |
| Split transactions   | Good                       | `S` / `E` / `$` rows map cleanly to Open-Finance split rows                                                                                   |
| Investment semantics | Limited                    | `!Type:Invst` rows are parsed only as cash-like imported transactions, not as portfolio lots or security holdings                             |

---

## Real Skrooge QIF constructs present in `my_export.qif`

The sample export contains these Skrooge QIF structures:

- `!Type:Cat` category definitions, for example:
    - `NMaison:Ménage`
    - `E` / `I` category polarity markers
- `!Account` blocks, for example:
    - `N00000463202`
    - `TBank`
    - `NAssurance Vie`
    - `TInvst`
- Standard transaction sections:
    - `!Type:Bank`
    - `!Type:Cash`
    - `!Type:Invst`
    - `!Type:Oth A`
- Category lines on transactions:
    - `LMaison:Ménage`
    - `LRevenus du travail:Salaire net`
- Transfer lines:
    - `L[00000463202]/Transfert`
    - `L[PERIN]/Transfert`
- Split lines:
    - `SAlimentation:Épicerie`
    - `EDU 181222 AFRO-EXOTIQUE.C ...`
    - `$-17.7`

---

## Import strategy in the current QIF flow

The generic QIF import path works in this order:

1. Parse the file into `ImportedTransaction` rows.
2. Preserve account context from `!Account` blocks.
3. Preserve category strings from transaction `L` rows and split `S` rows.
4. Detect bracketed transfer destinations from `L[Account]` syntax, including Skrooge’s `L[Account]/Transfert` form.
5. During confirmation, resolve or auto-create Open-Finance accounts from the imported account names.
6. Match or create categories from the imported category text.
7. Persist transactions, linked transfers, and split rows.

---

## Account mapping

### Skrooge QIF account context → Open-Finance account routing

| Open-Finance target      | Skrooge QIF source                                       | Current rule                                                                                        |
| ------------------------ | -------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| `Account.name`           | `!Account` → `N...`                                      | Parsed as the current account name and copied into each following `ImportedTransaction.accountName` |
| `Account.accountNumber`  | Not reliably preserved from generic QIF                  | Not populated by the current QIF parser for Skrooge exports                                         |
| `Account.currency`       | Not present per transaction in standard QIF sections     | Falls back to file/default/fallback account currency during confirmation                            |
| `Account.type`           | `!Account` → `TBank`, `TCash`, `TInvst`, `TOth A`        | **Not preserved today** in the generic QIF flow; auto-created accounts default to `CHECKING`        |
| `Account.openingDate`    | Earliest transaction date seen for that imported account | Used when auto-creating a missing account                                                           |
| `Account.initialBalance` | Not derived from Skrooge QIF account headers             | Missing accounts are auto-created with `0` initial balance                                          |

### Real examples from `my_export.qif`

| Skrooge QIF snippet                                      | Parsed account routing                                                          |
| -------------------------------------------------------- | ------------------------------------------------------------------------------- |
| `!Account` + `N00000463202` + `!Type:Bank`               | Transactions that follow are routed to account name `00000463202`               |
| `!Account` + `NAssurance Vie` + `!Type:Invst`            | Transactions that follow are routed to account name `Assurance Vie`             |
| `!Account` + `NRetraite (PER Article 83)` + `!Type:Cash` | Transactions that follow are routed to account name `Retraite (PER Article 83)` |

### Important limitation

Skrooge QIF contains account-type hints, but the current generic QIF flow uses those hints only as context for grouping transactions; it does **not** yet map `TBank`, `TCash`, `TInvst`, or `TOth A` into Open-Finance `AccountType` during auto-creation.

---

## Category mapping

### Standalone Skrooge category definitions

| Open-Finance target       | Skrooge QIF source                                  | Current rule                                      |
| ------------------------- | --------------------------------------------------- | ------------------------------------------------- |
| Category seed definitions | `!Type:Cat` rows such as `NMaison:Ménage`, `E`, `I` | **Not parsed directly** by the current QIF parser |
| Category type hint        | `E` / `I` in `!Type:Cat`                            | **Not preserved** in the generic QIF flow         |

### Transaction / split category text

| Open-Finance target           | Skrooge QIF source              | Current rule                                                                                                             |
| ----------------------------- | ------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| `Transaction.categoryId`      | Transaction `L...` line         | The full imported category string is preserved first, then matched by explicit mapping, exact name, or fuzzy review flow |
| `TransactionSplit.categoryId` | Split `S...` line               | Split category string is preserved, then matched by explicit mapping, exact string, or leaf-name fallback                |
| Transaction tags              | Category class suffix after `/` | Stored as `ImportedTransaction.tags` when present                                                                        |

### Real examples from `my_export.qif`

| Skrooge QIF field                 | Imported value                                                    | Open-Finance outcome                                     |
| --------------------------------- | ----------------------------------------------------------------- | -------------------------------------------------------- |
| `LMaison:Ménage`                  | `ImportedTransaction.category = "Maison:Ménage"`                  | Matched / created as a user category during confirmation |
| `LRevenus du travail:Salaire net` | `ImportedTransaction.category = "Revenus du travail:Salaire net"` | Matched / created as a user category during confirmation |
| `SAlimentation:Épicerie`          | `SplitEntry.category = "Alimentation:Épicerie"`                   | Saved as a split category mapping                        |
| `SDivers:Achat Divers`            | `SplitEntry.category = "Divers:Achat Divers"`                     | Saved as a split category mapping                        |

### Important limitation

In the generic QIF flow, Skrooge category hierarchy is preserved as text like `Maison:Ménage`, but there is no stable Skrooge category ID like in the JSON importer. Matching is therefore text-based rather than ID-based.

---

## Transaction mapping

### Standard transaction fields

| Open-Finance field  | Skrooge QIF source                                | Current rule                                                                                                     |
| ------------------- | ------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| `accountId`         | Current `!Account` block                          | Resolved from `ImportedTransaction.accountName` during confirmation                                              |
| `date`              | `D...`                                            | Parsed directly into `ImportedTransaction.transactionDate`                                                       |
| `type`              | Sign of `T...` / `U...`                           | Negative = `EXPENSE`, positive = `INCOME`, unless a linked transfer is created                                   |
| `amount`            | `T...` or fallback `U...`                         | Parsed with sign, then normalized to positive stored amount plus transaction type                                |
| `payee`             | `P...`                                            | Stored in Open-Finance `payee` and also used as `description`                                                    |
| `notes`             | `M...`                                            | Stored in Open-Finance `notes`                                                                                   |
| `externalReference` | `N...`                                            | Stored as Open-Finance `externalReference`                                                                       |
| `tags`              | Category class suffix or imported tags            | Stored as comma-separated Open-Finance tags                                                                      |
| `currency`          | Not reliably present in standard Skrooge QIF rows | Falls back to imported/default account currency                                                                  |
| Cleared status      | `C...`                                            | Parsed into `ImportedTransaction.clearedStatus`, but not currently persisted onto the final `Transaction` entity |

### Real examples from `my_export.qif`

| Skrooge QIF snippet          | Open-Finance mapping                                                       |
| ---------------------------- | -------------------------------------------------------------------------- |
| `D2022-10-06`                | `Transaction.date = 2022-10-06`                                            |
| `T-671.44`                   | `Transaction.type = EXPENSE`, `Transaction.amount = 671.44`                |
| `PPRLV SEPA`                 | `Transaction.payee = "PRLV SEPA"`, `Transaction.description = "PRLV SEPA"` |
| `MDébit  FREE TELECOM ...`   | `Transaction.notes = "Débit FREE TELECOM ..."`                             |
| `NBuy` in `!Type:Invst` rows | Stored as imported reference/action text                                   |

### Important limitation

Skrooge QIF balance snapshot rows are not specially identified in the generic QIF flow. For example, account-opening or current-balance lines like a standalone dated amount with no payee/category may still appear as ordinary imported transactions and should be reviewed before confirmation.

---

## Transfer mapping

### Bracketed transfer syntax

| Open-Finance field            | Skrooge QIF source       | Current rule                                                         |
| ----------------------------- | ------------------------ | -------------------------------------------------------------------- |
| `Transaction.type = TRANSFER` | `L[OtherAccount]`        | Parsed as a transfer                                                 |
| `toAccountId`                 | `L[OtherAccount]`        | The text inside brackets becomes `ImportedTransaction.toAccountName` |
| Transfer tags                 | `L[OtherAccount]/Class`  | The suffix after `/` is preserved as a tag                           |
| Source account                | Current `!Account` block | The account currently in scope becomes the transfer source           |

### Real Skrooge examples from `my_export.qif`

| Skrooge QIF line                            | Open-Finance mapping                                                             |
| ------------------------------------------- | -------------------------------------------------------------------------------- |
| `L[00000463202]/Transfert`                  | Linked transfer to account name `00000463202`, with tag `Transfert`              |
| `L[PERIN]/Transfert`                        | Linked transfer to account name `PERIN`, with tag `Transfert`                    |
| `L[00040151407]/Transfert` in `!Type:Invst` | Linked transfer between the investment wrapper account and account `00040151407` |

### Ambiguous transfer case

| Skrooge QIF line | Current behavior                                                                  |
| ---------------- | --------------------------------------------------------------------------------- |
| `LTransfert`     | Imported as an ordinary category string because no destination account is encoded |

If Skrooge emits an unbracketed transfer category without the destination account, Open-Finance cannot create a linked transfer automatically. That row requires manual review.

---

## Split transaction mapping

### Parent split transaction

| Open-Finance field | Skrooge QIF source                               | Current rule                                                    |
| ------------------ | ------------------------------------------------ | --------------------------------------------------------------- |
| Parent transaction | `D`, `T`, `P`, `M` plus one or more `S/$` groups | Parent row becomes one Open-Finance `Transaction`               |
| Parent type        | Sign of parent `T`                               | Negative parent = expense split, positive parent = income split |
| Parent amount      | Parent `T`                                       | Stored as normalized parent amount                              |

### Split rows

| Open-Finance field             | Skrooge QIF source | Current rule                                                 |
| ------------------------------ | ------------------ | ------------------------------------------------------------ |
| `TransactionSplit.categoryId`  | `S...`             | Split category text is matched / created during confirmation |
| `TransactionSplit.description` | `E...`             | Split memo becomes the split description                     |
| `TransactionSplit.amount`      | `$...`             | Split amount is parsed and normalized                        |

### Real split example from `my_export.qif`

```qif
D2022-12-19
T-39.6
PFACTURE CARTE
MDébit  DU 181222 AFRO-EXOTIQUE.C ...
SAlimentation:Épicerie
EDU 181222 AFRO-EXOTIQUE.C ...
$-17.7
SDivers:Achat Divers
$-7.6
SDivers:Achat Divers
EExpédition
$-14.3
^
```

This becomes:

- one parent Open-Finance expense transaction for `39.60`
- one split row for `Alimentation:Épicerie` amount `17.70`
- one split row for `Divers:Achat Divers` amount `7.60`
- one split row for `Divers:Achat Divers` amount `14.30` with description `Expédition`

### Split validation rule

The QIF parser validates that the absolute sum of split amounts equals the absolute parent transaction amount.

---

## Investment-section behavior

Skrooge exports several accounts as `!Type:Invst`, for example `Assurance`, `Assurance Vie`, and crypto/security wrappers.

Current behavior in the generic QIF flow:

| Skrooge QIF field                                   | Current mapping                                                                   |
| --------------------------------------------------- | --------------------------------------------------------------------------------- |
| `!Type:Invst`                                       | Parsed with the same imported-transaction DTO, not as a dedicated asset-lot model |
| `N...` action (`Buy`, `Sell`, etc.)                 | Stored as imported `referenceNumber`                                              |
| `Y...` security symbol (`BTC`, `ADA`, `B504`, etc.) | Stored as imported `payee`                                                        |
| `T...` amount                                       | Imported as cash-like transaction amount                                          |

This means QIF investment exports are only partially preserved compared with the Skrooge JSON importer.

---

## Recommended use

For Skrooge migrations into Open-Finance:

1. Prefer [docs/SKROOGE_JSON_IMPORT_MAPPING.md](docs/SKROOGE_JSON_IMPORT_MAPPING.md) whenever JSON export is available.
2. Use QIF when you need a bank-ledger style import and can tolerate reduced fidelity.
3. Review these QIF-specific edge cases carefully before confirming import:
    - balance snapshot rows
    - unbracketed `Transfert` category rows
    - auto-created account types defaulting to `CHECKING`
    - investment accounts exported as `!Type:Invst`

---

## Bottom line

Skrooge QIF can now map reliably into Open-Finance for:

- account-aware transaction routing
- categorized income/expense transactions
- bracketed inter-account transfers
- split transactions

But unlike Skrooge JSON, QIF still loses stable source IDs, category-definition metadata, rich account metadata, and full investment semantics.

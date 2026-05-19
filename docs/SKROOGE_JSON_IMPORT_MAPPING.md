# Skrooge JSON → Open-Finance Import Mapping

## Goal

This document defines the highest-fidelity migration path from a Skrooge JSON export into Open-Finance.
It is based on the real Skrooge export structure found in `skrooge_exports/my_export.json` and the current Open-Finance domain/import model.

**Status:** Open-Finance now includes a dedicated Skrooge JSON importer instead of requiring conversion to CSV/QIF first. JSON remains the highest-fidelity migration path because it preserves accounts, institutions, categories, transfers, split lines, currencies, and stable internal IDs.

---

## Source collections to use

The Skrooge JSON export contains many collections, but the importer should primarily use these:

| Skrooge collection | Purpose in importer                                            |
| ------------------ | -------------------------------------------------------------- |
| `bank`             | Institution source data                                        |
| `account`          | Account source data                                            |
| `category`         | Hierarchical category source data                              |
| `payee`            | Payee source data and optional category hints                  |
| `operation`        | Parent transaction rows                                        |
| `suboperation`     | Transaction line items, split lines, transfer category markers |
| `unit`             | Currency / asset unit metadata                                 |
| `unitvalue`        | Optional valuation history for non-cash assets                 |
| `operationbalance` | Optional running balance verification                          |
| `refund`           | Optional refund metadata                                       |

Collections such as `budget`, `rule`, `doctransaction`, `interest`, and `parameters` are outside the scope of a first-pass transaction importer.

---

## Core relational joins

The importer should materialize these joins first:

| From               | Field             | To             | Meaning                           |
| ------------------ | ----------------- | -------------- | --------------------------------- |
| `account`          | `rd_bank_id`      | `bank.id`      | Account institution               |
| `operation`        | `rd_account_id`   | `account.id`   | Transaction account               |
| `operation`        | `r_payee_id`      | `payee.id`     | Transaction payee                 |
| `operation`        | `rc_unit_id`      | `unit.id`      | Transaction currency / asset unit |
| `suboperation`     | `rd_operation_id` | `operation.id` | Transaction lines                 |
| `suboperation`     | `r_category_id`   | `category.id`  | Line category                     |
| `category`         | `rd_category_id`  | `category.id`  | Category parent                   |
| `unitvalue`        | `rd_unit_id`      | `unit.id`      | Historical price / FX value       |
| `operationbalance` | `r_operation_id`  | `operation.id` | Running balance snapshot          |

---

## Import strategy

### 1. Import order

Use this order to preserve referential integrity:

1. `bank` → `Institution`
2. `unit` → currency resolution table
3. `account` → `Account`
4. `category` → `Category` roots, then children
5. `payee` → payee lookup table (not a persistent entity in Open-Finance)
6. `operation` + `suboperation` → `Transaction` and `TransactionSplit`

### 2. Stable identifiers

For best duplicate detection and idempotent re-imports, the importer should synthesize stable external IDs:

- Regular transaction parent: `skrooge:operation:{operation.id}`
- Split line: keep only on `TransactionSplit`, not in `Transaction.externalReference`
- Transfer pair: `skrooge:transfer-group:{operation.i_group_id}` as `transferId`
- Account import key: `skrooge:account:{account.id}` in importer-side mapping table
- Category import key: `skrooge:category:{category.id}` in importer-side mapping table

Do **not** rely only on fuzzy matching. Skrooge `operation.id`, `suboperation.id`, and grouped transfer `i_group_id` are much more reliable than QIF/CSV-style references.

### 3. Synthetic balance rows

Rows where:

- `operation.d_date == "0000-00-00"`
- and/or `operation.t_mode == ""`
- and/or the operation has a single `suboperation` with no category and looks like a balance snapshot

should **not** be imported as ordinary transactions.

Instead:

- use them to derive `Account.openingBalance` or initial reconciliation context,
- or ignore them if a better opening balance is computed from imported ledger history.

In the sample export, rows like operation `1848` represent account state, not a user transaction.

---

## Account mapping

### Skrooge `account` → Open-Finance `Account`

| Open-Finance field | Skrooge source                                                                    | Rule                                                                                                            |
| ------------------ | --------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| `name`             | `account.t_name`                                                                  | Primary display name                                                                                            |
| `accountNumber`    | `account.t_number`                                                                | Use when non-blank; fallback to `account.t_name` if it is a bank-like numeric identifier                        |
| `institution`      | `bank.t_name` via `account.rd_bank_id`                                            | Upsert or match `Institution.name` case-insensitively                                                           |
| `currency`         | `unit` joined from account’s balance operation or dominant `operation.rc_unit_id` | Prefer ISO currency code extracted from `unit.t_name` / `unit.t_internet_code`; fallback to unit symbol mapping |
| `description`      | `account.t_comment`                                                               | Preserve as description                                                                                         |
| `openingBalance`   | synthetic balance operation / earliest reconciled balance                         | Prefer synthetic `0000-00-00` balance row if present; otherwise compute from imported history                   |
| `openingDate`      | earliest valid transaction date for this account                                  | Ignore `0000-00-00` rows                                                                                        |
| `isActive`         | `!account.t_close`                                                                | Closed Skrooge account → inactive Open-Finance account                                                          |
| `type`             | `account.t_type`                                                                  | Map using rules below                                                                                           |
| `balance`          | derived after import                                                              | Let Open-Finance recalculate from transactions                                                                  |

### Account type mapping

Skrooge account types are single-character codes. Use the following **recommended** mapping:

| Skrooge `account.t_type` | Meaning (practical)                          | Open-Finance `AccountType` | Notes                                                                          |
| ------------------------ | -------------------------------------------- | -------------------------- | ------------------------------------------------------------------------------ |
| `C`                      | current/checking                             | `CHECKING`                 | deterministic                                                                  |
| `S`                      | savings                                      | `SAVINGS`                  | deterministic                                                                  |
| `I`                      | investment wrapper                           | `INVESTMENT`               | deterministic                                                                  |
| `P`                      | pension / retirement / plan                  | `INVESTMENT`               | best fit in current model                                                      |
| `W`                      | wallet / cash-like store                     | `CASH`                     | best fit                                                                       |
| `A`                      | asset / miscellaneous holding                | `OTHER`                    | may later become `INVESTMENT` or asset import                                  |
| `L`                      | liability / loan-like account                | `OTHER`                    | current account model is a weak fit; prefer separate liability migration later |
| `D`                      | debt/deposit/other institution-specific type | `OTHER`                    | manual review recommended                                                      |

### Skrooge `bank` → Open-Finance `Institution`

| Open-Finance field | Skrooge source                                       | Rule                                                                                  |
| ------------------ | ---------------------------------------------------- | ------------------------------------------------------------------------------------- |
| `name`             | `bank.t_name`                                        | Required                                                                              |
| `logo`             | `bank.t_icon`                                        | Optional later enhancement; not directly compatible unless icon asset can be resolved |
| `bic`              | —                                                    | Not present in sample export                                                          |
| `country`          | derive from `account.t_agency_address` or leave null | Optional heuristic only                                                               |
| `isSystem`         | constant                                             | `false` for imported institutions                                                     |

---

## Category mapping

### Skrooge `category` → Open-Finance `Category`

| Open-Finance field | Skrooge source            | Rule                                  |
| ------------------ | ------------------------- | ------------------------------------- |
| `name`             | `category.t_name`         | Use the leaf name only                |
| `parentId`         | `category.rd_category_id` | Resolve after parent category import  |
| `type`             | inferred                  | See rule below                        |
| `isSystem`         | constant                  | `false`                               |
| `icon`             | constant/default          | Optional default, e.g. `tag`          |
| `color`            | constant/default          | Optional default                      |
| `nameKey`          | —                         | Not used for imported user categories |

### Category type inference

Open-Finance requires `CategoryType` = `INCOME` or `EXPENSE`, while Skrooge categories are structurally neutral.
Infer type using this priority:

1. If the category is used only by positive `suboperation.f_value` rows → `INCOME`
2. If the category is used only by negative `suboperation.f_value` rows → `EXPENSE`
3. If mixed, use the parent category name and known lexical hints:
    - income-like: `revenus`, `salaire`, `intérêts`, `cadeaux reçus`, `revente`, `plus-values`
    - expense-like: `alimentation`, `maison`, `santé`, `soins`, `automobile`, `loyer`, `taxes`
4. If still ambiguous, default to `EXPENSE` and flag for manual review

### Category path handling

Skrooge stores both:

- `t_name` = leaf name
- `t_fullname` = full hierarchy path (for example `Maison > Ménage`)

Importer rules:

- Preserve hierarchy by creating parent categories from `rd_category_id`
- Use `t_name` in `Category.name`
- Store `t_fullname` in the importer cache only, not in the final entity
- Match split categories and transaction categories by Skrooge category ID first, never by path text when IDs are available

---

## Transaction mapping

### Parent transaction model

A Skrooge logical transaction is:

- one `operation` row,
- plus one or more `suboperation` rows.

Interpretation:

- **1 suboperation** → normal income/expense/transfer-side line
- **2+ suboperations** → split transaction
- **shared `i_group_id > 0` across two operations with transfer category** → transfer pair

### Skrooge `operation` + first-class line resolution → Open-Finance `Transaction`

| Open-Finance field  | Skrooge source                                                                                                              | Rule                                                                                              |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| `userId`            | importer context                                                                                                            | Required runtime context                                                                          |
| `accountId`         | `operation.rd_account_id` mapped through imported accounts                                                                  | Required                                                                                          |
| `date`              | `operation.d_date`                                                                                                          | Parse ISO date; skip `0000-00-00` synthetic rows                                                  |
| `amount`            | sum of absolute `suboperation.f_value` for non-transfer normal/split parent; transfer side absolute amount for transfer ops | Open-Finance stores positive amount                                                               |
| `currency`          | `unit` from `operation.rc_unit_id`                                                                                          | Convert to ISO 4217 when possible                                                                 |
| `description`       | payee name or operation label                                                                                               | Prefer resolved `payee.t_name`; fallback to `operation.t_comment`; fallback to `operation.t_mode` |
| `notes`             | `operation.t_comment` plus non-primary metadata                                                                             | Preserve original comment and import metadata as note text if needed                              |
| `payee`             | `payee.t_name` via `operation.r_payee_id`                                                                                   | If payee is empty/placeholder, fallback to parsed merchant from `t_comment`                       |
| `type`              | derived from line values / transfer rules                                                                                   | `INCOME`, `EXPENSE`, or transfer pair handling below                                              |
| `categoryId`        | single `suboperation.r_category_id` only                                                                                    | Set only for non-split, non-transfer transactions                                                 |
| `paymentMethod`     | derived from `operation.t_mode` and `t_comment`                                                                             | Optional enrichment                                                                               |
| `externalReference` | synthesized                                                                                                                 | `skrooge:operation:{operation.id}`                                                                |
| `isReconciled`      | `operation.t_status == "Y"`                                                                                                 | Recommended mapping                                                                               |
| `isDeleted`         | constant                                                                                                                    | `false`                                                                                           |
| `tags`              | optional from `operation.t_bookmarked`, imported flags                                                                      | Optional enrichment only                                                                          |
| `toAccountId`       | transfer only                                                                                                               | Do not set for non-transfer rows                                                                  |
| `transferId`        | transfer only                                                                                                               | Do not set for non-transfer rows                                                                  |

### Transaction type derivation

| Skrooge condition                     | Open-Finance result                                         |
| ------------------------------------- | ----------------------------------------------------------- |
| single `suboperation.f_value < 0`     | `EXPENSE`                                                   |
| single `suboperation.f_value > 0`     | `INCOME`                                                    |
| split transaction where total sum < 0 | parent `EXPENSE`                                            |
| split transaction where total sum > 0 | parent `INCOME`                                             |
| grouped transfer (`i_group_id > 0`)   | import as linked transfer pair, not ordinary income/expense |

### Payment method derivation (optional but recommended)

| Skrooge signal                                 | Open-Finance `PaymentMethod`                         |
| ---------------------------------------------- | ---------------------------------------------------- |
| `operation.t_comment` contains `FACTURE CARTE` | `DEBIT_CARD`                                         |
| `operation.t_comment` contains `PRLV SEPA`     | `DIRECT_DEBIT`                                       |
| `operation.t_comment` contains `VIR SEPA`      | `BANK_TRANSFER` or `DEPOSIT` for salary-like credits |
| `operation.t_comment` contains `RETRAIT DAB`   | `CASH`                                               |
| positive salary/income credit                  | `DEPOSIT`                                            |
| otherwise                                      | `OTHER`                                              |

---

## Split transaction mapping

Open-Finance has native split support through `TransactionSplit`, so Skrooge split transactions should be imported losslessly.

### Detection

A Skrooge transaction is a split transaction when an `operation` has **multiple** `suboperation` rows after excluding synthetic balance rows.

### Parent `Transaction`

For a split transaction:

| Field               | Rule                               |
| ------------------- | ---------------------------------- |
| `type`              | from total sign of all split lines |
| `amount`            | sum of absolute split amounts      |
| `categoryId`        | `null`                             |
| `externalReference` | `skrooge:operation:{operation.id}` |
| `description`       | resolved payee or comment          |
| `notes`             | `operation.t_comment`              |

### `TransactionSplit`

Each Skrooge `suboperation` becomes one Open-Finance `TransactionSplit`.

| Open-Finance split field | Skrooge source                 | Rule                                                                 |
| ------------------------ | ------------------------------ | -------------------------------------------------------------------- |
| `transactionId`          | parent imported transaction ID | Assigned after parent save                                           |
| `categoryId`             | `suboperation.r_category_id`   | Resolve through imported category ID map                             |
| `amount`                 | `abs(suboperation.f_value)`    | Always positive in Open-Finance                                      |
| `description`            | `suboperation.t_comment`       | Fallback to parent `operation.t_comment` for the first line if blank |

### Split validation rule

Before save:

- `sum(TransactionSplit.amount) == Transaction.amount`
- allow only rounding tolerance already accepted by Open-Finance (±0.01)

### Example

Skrooge `operation.id = 112` contains 3 `suboperation` rows:

- `-17.70` → `Alimentation > Épicerie`
- `-7.60` → `Divers > Achat Divers`
- `-14.30` → `Divers > Achat Divers`, comment `Expédition`

Import as:

- parent `Transaction` = `EXPENSE`, amount `39.60`, no category
- 3 `TransactionSplit` rows with positive amounts `17.70`, `7.60`, `14.30`

---

## Transfer mapping

Skrooge represents transfers as **two operations** sharing the same `i_group_id`, usually with category `Transfert`.

Open-Finance persists transfers as a **linked pair** of transactions with the same `transferId`.

### Detection

Treat a set of operations as one transfer when all are true:

1. `operation.i_group_id > 0`
2. exactly two operations share that `i_group_id`
3. each operation has exactly one `suboperation`
4. both `suboperation.r_category_id` resolve to category `Transfert` (or equivalent imported transfer category)
5. the two line amounts are opposite in sign or represent mirrored source/destination amounts

### Mapping rule

For a detected transfer group:

| Open-Finance field           | Rule                                                                                  |
| ---------------------------- | ------------------------------------------------------------------------------------- |
| `transferId`                 | `skrooge:transfer-group:{i_group_id}`                                                 |
| source account               | operation whose economic effect is money leaving the source account                   |
| destination account          | the other operation’s account                                                         |
| amount                       | absolute value of the source-side amount                                              |
| source transaction type      | `EXPENSE`                                                                             |
| destination transaction type | `INCOME`                                                                              |
| categoryId                   | `null` on both sides                                                                  |
| externalReference            | `skrooge:operation:{operation.id}` per persisted side                                 |
| notes                        | preserve source `operation.t_comment` on both sides or store direction-specific notes |

### Important rule

Do **not** import transfer-category Skrooge rows as ordinary categorized income/expense transactions. They must become linked transfers in Open-Finance, or balances and reporting will be wrong.

### Example

Skrooge group `30` contains:

- operation `150` on account `00000463202`
- operation `1885` on account `Crowdlending`
- both categorized as `Transfert`
- amount `500`

Import as one Open-Finance transfer pair with:

- `transferId = skrooge:transfer-group:30`
- source = `00000463202`
- destination = `Crowdlending`
- amount = `500.00`

---

## Payee mapping

Open-Finance does not persist payees as a separate entity, so Skrooge `payee` is a lookup source only.

| Open-Finance field        | Skrooge source                            | Rule                                                                   |
| ------------------------- | ----------------------------------------- | ---------------------------------------------------------------------- |
| `Transaction.payee`       | `payee.t_name` via `operation.r_payee_id` | Primary mapping                                                        |
| `Transaction.description` | `payee.t_name` or merchant text           | Use payee when meaningful, otherwise use comment-derived merchant text |
| category hint             | `payee.r_category_id`                     | Optional fallback if transaction/suboperation category missing         |

### Special handling

Some Skrooge payees are generic placeholders such as `FACTURE CARTE`, `PRLV SEPA`, or `VIR SEPA RECU`.
For those values:

- keep `payee` if you want source fidelity,
- but consider deriving a richer `description` from `operation.t_comment`.

Recommended precedence:

1. if payee is specific → `payee.t_name`
2. else if `t_comment` contains merchant details → parse merchant from comment
3. else fallback to `operation.t_mode`

---

## Currency mapping

### Skrooge `unit` → Open-Finance currency code

| Skrooge source                                             | Rule                                                                                                     |
| ---------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| `unit.t_name` contains `(EUR)` / `(USD)` style suffix      | extract ISO code inside parentheses                                                                      |
| `unit.t_internet_code` contains `USD/EUR`, `BTC-EUR`, etc. | derive left-side asset code when useful, but use only ISO fiat currency for account/transaction currency |
| `unit.t_symbol` = `€`, `$`, `CFA`                          | map via symbol lookup if no ISO code available                                                           |

Recommended unit resolution table:

| Skrooge unit example                                              | Open-Finance currency                                                                             |
| ----------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| `Euro (EUR)`                                                      | `EUR`                                                                                             |
| `Dollar américain (USD)`                                          | `USD`                                                                                             |
| `Franc CFA de l'Afrique de l'Ouest (XOF)`                         | `XOF`                                                                                             |
| crypto/security units like `BTC`, `ADA`, `SOL`, `TCALAVI`, `B504` | not valid transaction fiat currency; use account-level strategy or skip until asset import exists |

### Non-fiat caution

Open-Finance `Transaction.currency` expects a 3-letter ISO currency code. Skrooge units such as `BTC`, `ADA`, `DOGE`, `TCALAVI`, or property-like units are asset/security units, not fiat currencies.

For a first transaction importer:

- import only cash/bank-style accounts and their fiat-denominated transactions,
- skip or separately migrate security/valuation data,
- or map those accounts to `OTHER` without importing price-history as transactions.

---

## Fields that should not be forced into Open-Finance transactions

| Skrooge field                 | Reason                                                            |
| ----------------------------- | ----------------------------------------------------------------- |
| `operationbalance.f_balance`  | Verification / audit only, not a transaction field                |
| `unitvalue` history           | Better suited for future asset valuation import                   |
| `interest`, `interest_result` | Not part of ordinary transaction import                           |
| `doctransaction*`             | Document workflow metadata, not ledger activity                   |
| `rule`, `budget*`             | Can be migrated later, but not needed for transaction correctness |
| `parameters`                  | Application settings, not user ledger records                     |

---

## Implementation checklist

### Recommended importer pipeline

1. Load full JSON into indexed maps by ID
2. Import `bank` as institutions
3. Import `account` as accounts
4. Import `category` hierarchy and build `skroogeCategoryId -> openFinanceCategoryId`
5. Group `suboperation` by `rd_operation_id`
6. Ignore synthetic balance operations (`0000-00-00`)
7. Detect transfer groups by `i_group_id`
8. Import transfer groups as linked transfer pairs
9. Import remaining operations:
    - one line → normal transaction
    - multiple lines → parent transaction + splits
10. Persist `externalReference = skrooge:operation:{id}`
11. Recalculate balances after import
12. Run idempotency check by re-importing the same file and asserting zero new transactions

### Validation assertions

For a correct migration:

- every imported normal operation maps to exactly 1 `Transaction`
- every imported split operation maps to 1 parent `Transaction` + `n` `TransactionSplit`
- every transfer group maps to exactly 2 linked transfer transactions
- no `0000-00-00` synthetic balance rows become user transactions
- total account balances after import reconcile with expected opening balance + imported activity
- re-import is idempotent using synthesized `externalReference` / `transferId`

---

## Practical conclusion

For the most accurate Skrooge migration into Open-Finance:

- use **Skrooge JSON** as the source of truth,
- import **accounts, institutions, category hierarchy, transfers, and splits natively**,
- synthesize **stable external references** from Skrooge IDs,
- and **do not flatten** transfers or split lines into ordinary CSV-like transactions.

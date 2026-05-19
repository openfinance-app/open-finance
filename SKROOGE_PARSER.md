User: Some users of the open-finance app will comes from Skrooge.
Considering the export files in skrooge_exports

which one is the best most efficient way to consistently and accuretly import data from Skrooge to open-finance.

You must analyse the format of each the skrooge exports and tell which one is the most prone to consistency and accuracy in data import into open-finance

GitHub Copilot: I’ll inspect each Skrooge export sample, compare how much structure and fidelity each format preserves, then rank them for reliable import into open-finance.

Created 3 todos

I’m pulling the actual sample contents now so the recommendation is based on the real Skrooge exports, not generic format assumptions.

Read [](file:///home/pain/NetBeansProjects/open-finance/skrooge_exports/my_export.csv#1-1), lines 1 to 250

Read [](file:///home/pain/NetBeansProjects/open-finance/skrooge_exports/my_export.json)

Read [](file:///home/pain/NetBeansProjects/open-finance/skrooge_exports/my_export.qif#1-1), lines 1 to 300

Read [](file:///home/pain/NetBeansProjects/open-finance/skrooge_exports/my_export.xml#1-1), lines 1 to 400

I’ve got strong signals already: CSV is flat, QIF is semi-structured, XML is richly structured. I still need the JSON sample because that could beat XML if it’s a clean full-fidelity export.

Starting: _Compare import fidelity_ (2/3)

I’m checking the JSON export separately now; once I see its schema, I can give a defensible ranking instead of a tentative one.

Read [](file:///home/pain/NetBeansProjects/open-finance/skrooge_exports/my_export.json)

Ran terminal command: python - <<'PY'
from pathlib import Path
p = Path('/home/pain/NetBeansProjects/open-finance/skrooge_exports/my_export.json')
print('size', p.stat().st_size)
with p.open('rb') as f:
data = f.read(400)
print(data[:400])
PY

I found the JSON issue: it’s valid and large, not empty. Next I’m summarizing its schema and checking what open-finance already supports so the recommendation matches the app reality.

Ran terminal command: python - <<'PY'
import json
from pathlib import Path
p = Path('/home/pain/NetBeansProjects/open-finance/skrooge_exports/my_export.json')
with p.open() as f:
data = json.load(f)
print('top-level keys:', sorted(data.keys()))
for key in sorted(data.keys()):
value = data[key]
if isinstance(value, list):
print(f'[{key}] count={len(value)} sample_keys={sorted(value[0].keys())[:20] if value else []}')
else:
print(f'[{key}] type={type(value).**name**}')
PY

Searched for regex `QIF|CSV|OFX|Skrooge|import format|import` (`**/{README.md,docs/**,src/**,openfinance-ui/src/**}`), 200 results

The app reality matters here: open-finance appears to accept `qif`, `ofx/qfx`, and `csv` today, which changes the “best” answer from “theoretically richest” to “best with minimal conversion risk.”

Searched for regex `allowed-extensions|qif|ofx|qfx|csv` (`**/src/main/**`), 200 results

Searched for regex `Import Guide|QIF|CSV|OFX|QFX` (`**/docs/**`), 200 results

Ran terminal command: python - <<'PY'
import json
from pathlib import Path
p = Path('/home/pain/NetBeansProjects/open-finance/skrooge_exports/my_export.json')
with p.open() as f:
data = json.load(f)
for key in ['account','bank','category','payee','operation','suboperation','unit','unitvalue','operationbalance']:
items = data[key]
print('\n###', key, 'count=', len(items))
print(json.dumps(items[0], ensure_ascii=False, indent=2)[:2000])
PY

I’ve got enough to separate “best source format overall” from “best format open-finance can use today.” I’m just validating one last point: how Skrooge represents splits/transfers in QIF versus CSV.

Ran terminal command: python - <<'PY'
from pathlib import Path
p = Path('/home/pain/NetBeansProjects/open-finance/skrooge_exports/my_export.qif')
text = p.read_text(errors='replace')
for token in ['\nS', '\nE', '\n$', '!Account', '!Type:Bank', 'L[', 'LTransfert', 'L[october]']:
print(token, text.count(token))
PY

Ran terminal command: python - <<'PY'
from pathlib import Path
p = Path('/home/pain/NetBeansProjects/open-finance/skrooge_exports/my_export.csv')
with p.open(encoding='utf-8', errors='replace') as f:
for i, line in enumerate(f, 1):
if ';"112";"0"' in line or ';"1885";"30"' in line or '"0000-00-00"' in line:
print(i, line.rstrip())
if i > 250: break
PY

That confirms the core risk: Skrooge’s CSV flattens splits into multiple rows and mixes in non-transaction balance/account rows, while QIF keeps native split markers and transfer semantics.

Ran terminal command: python - <<'PY'
from pathlib import Path
lines = Path('/home/pain/NetBeansProjects/open-finance/skrooge_exports/my_export.qif').read_text(errors='replace').splitlines()

# first split block

for i, line in enumerate(lines):
if line.startswith('S'):
start = max(0, i-6)
end = min(len(lines), i+8)
print('--- split example ---')
for j in range(start, end):
print(lines[j])
break

# first transfer block

for i, line in enumerate(lines):
if line.startswith('L['):
start = max(0, i-6)
end = min(len(lines), i+6)
print('--- transfer example ---')
for j in range(start, end):
print(lines[j])
break
PY

Read [](file:///home/pain/NetBeansProjects/open-finance/docs/IMPORT_GUIDE.md#20-20), lines 20 to 150

I’ve finished the comparison. I’m wrapping it into a practical recommendation: what to use now in open-finance, and what to use if you later want a dedicated Skrooge migration path.

Completed: _Compare import fidelity_ (2/3)

**Verdict**

- For importing Skrooge data into open-finance today, the best choice is **QIF**.
- For a future **custom Skrooge-specific importer**, the best source format is **JSON**, with **XML** second.
- The worst option for consistency and accuracy is **CSV**.

**Why**

- my_export.qif preserves real transaction structure: payee, memo, status, categories, **splits** (`S` / `E` / `$`) and **transfers** (`L[...]`). That is much closer to open-finance’s current transaction import flow in IMPORT_GUIDE.md, and open-finance already supports `.qif` in application-prod.yml.
- my_export.csv is flat and ambiguous: it mixes **non-transaction rows** with date `0000-00-00`, flattens split transactions into multiple rows, and relies on `idtransaction` / `idgroup` to reconstruct meaning. That makes it the most error-prone for duplicate imports, split handling, and transfer reconstruction.
- my_export.json is the richest export overall: it contains normalized entities like `account`, `operation`, `suboperation`, `payee`, `category`, `unit`, `unitvalue`, and `operationbalance`. That makes it the best **technical source of truth**, but open-finance does not have a native Skrooge-JSON import path.
- my_export.xml is also very complete and structured, but it is more verbose and slightly harder to transform reliably than JSON.

**Ranking**

- `1.` **QIF** — best direct import into open-finance now
- `2.` **JSON** — best for building a high-fidelity dedicated migration tool
- `3.` **XML** — nearly as complete as JSON, but less convenient to process
- `4.` **CSV** — easiest to open manually, worst for faithful automated import

**Recommendation**

- If users are importing **right now in the existing app**, tell them to export **QIF** from Skrooge.
- If you want the **most accurate migration possible**, build a Skrooge importer from **JSON** and avoid CSV entirely.

If you want, I can next produce a precise field-mapping table from Skrooge `QIF` or `JSON` into open-finance accounts, transactions, categories, transfers, and split transactions.

#!/bin/bash
# scripts/check-line-counts.sh
# Maven build lifecycle hook: reports code stats and enforces max file length.
set -u

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
FAIL_THRESHOLD=3500
HAS_ERROR=0

# ── Tool detection ────────────────────────────────────────────────
TOOL=""
if command -v tokei &>/dev/null; then
    TOOL="tokei"
elif command -v cloc &>/dev/null; then
    TOOL="cloc"
else
    echo "[WARN] Neither tokei nor cloc found. Skipping line count checks."
    exit 0
fi

BACKEND_SRC="$PROJECT_DIR/src/main"
FRONTEND_SRC="$PROJECT_DIR/openfinance-ui/src"

echo "=========================================="
echo "  Code Line Count Report  ($TOOL)"
echo "=========================================="
echo ""

# ── Summary via tokei ─────────────────────────────────────────────
if [ "$TOOL" = "tokei" ]; then
    echo "--- Backend (Java) ---"
    tokei "$BACKEND_SRC" -t=Java --output json 2>/dev/null | python3 -c "
import json, sys
d = json.load(sys.stdin)
j = d.get('Java', {})
if isinstance(j, dict) and j.get('reports'):
    n = len(j['reports'])
    t = j['code'] + j['comments'] + j['blanks']
    print(f'  Files: {n},  Code: {j[\"code\"]},  Comments: {j[\"comments\"]},  Blanks: {j[\"blanks\"]},  Total: {t}')
"
    echo ""
    echo "--- Frontend (TypeScript / JavaScript) ---"
    tokei "$FRONTEND_SRC" -t=TypeScript,TSX,JavaScript -e '*.test.*' -e '*.spec.*' -e '*/test/*' --output json 2>/dev/null | python3 -c "
import json, sys
d = json.load(sys.stdin)
for lang in ('TypeScript', 'TSX', 'JavaScript'):
    j = d.get(lang, {})
    if isinstance(j, dict) and j.get('reports'):
        n = len(j['reports'])
        t = j['code'] + j['comments'] + j['blanks']
        print(f'  {lang}: {n} files, {j[\"code\"]} code, {j[\"comments\"]} comments, {j[\"blanks\"]} blanks, {t} total')
"
else
    echo "--- Backend (Java) ---"
    cloc "$BACKEND_SRC" --include-lang=Java --quiet 2>/dev/null | tail -3
    echo "--- Frontend (TypeScript / JavaScript) ---"
    cloc "$FRONTEND_SRC" --include-lang=TypeScript,TSX,JavaScript --exclude-dir=test,__tests__ --quiet 2>/dev/null | tail -3
fi

echo ""

# ── Top 3 biggest files ───────────────────────────────────────────
echo "--- Top 3 Biggest Backend Files (Java) ---"
find "$BACKEND_SRC" -name '*.java' ! -path '*/test/*' -exec wc -l {} + 2>/dev/null \
    | grep -v ' total$' | sort -rn | head -3 | awk '{printf "  %6d  %s\n", $1, $2}' \
    | sed "s|$PROJECT_DIR/||g"
echo ""

echo "--- Top 3 Biggest Frontend Files (TS/JS) ---"
find "$FRONTEND_SRC" \( -name '*.ts' -o -name '*.tsx' -o -name '*.js' -o -name '*.jsx' \) \
    ! -path '*/test/*' ! -name '*.test.*' ! -name '*.spec.*' \
    -exec wc -l {} + 2>/dev/null \
    | grep -v ' total$' | sort -rn | head -3 | awk '{printf "  %6d  %s\n", $1, $2}' \
    | sed "s|$PROJECT_DIR/||g"
echo ""

# ── Enforce max file size ─────────────────────────────────────────
echo "--- File Size Limit Check (max ${FAIL_THRESHOLD} lines) ---"

big_backend=$(find "$BACKEND_SRC" -name '*.java' ! -path '*/test/*' -exec wc -l {} + 2>/dev/null \
    | grep -v ' total$' | awk -v t="$FAIL_THRESHOLD" '$1 > t {print}')

if [ -n "$big_backend" ]; then
    echo "[ERROR] Backend files exceeding ${FAIL_THRESHOLD} lines:"
    echo "$big_backend" | awk '{printf "  %6d  %s\n", $1, $2}' | sed "s|$PROJECT_DIR/||g"
    HAS_ERROR=1
fi

big_frontend=$(find "$FRONTEND_SRC" \( -name '*.ts' -o -name '*.tsx' -o -name '*.js' -o -name '*.jsx' \) \
    ! -path '*/test/*' ! -name '*.test.*' ! -name '*.spec.*' \
    -exec wc -l {} + 2>/dev/null \
    | grep -v ' total$' | awk -v t="$FAIL_THRESHOLD" '$1 > t {print}')

if [ -n "$big_frontend" ]; then
    echo "[ERROR] Frontend files exceeding ${FAIL_THRESHOLD} lines:"
    echo "$big_frontend" | awk '{printf "  %6d  %s\n", $1, $2}' | sed "s|$PROJECT_DIR/||g"
    HAS_ERROR=1
fi

if [ "$HAS_ERROR" -eq 1 ]; then
    echo ""
    echo "[FAIL] Some files exceed ${FAIL_THRESHOLD} lines. Refactor to continue."
    exit 1
fi

echo "  All files within limits."
exit 0

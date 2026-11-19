#!/bin/sh
# Checks the kit's documents. Exit 0 = pass; 1 = one "FAIL: ..." line per problem.
# Run from anywhere: sh tools/check-docs.sh
cd "$(dirname "$0")/.." || exit 2

fails=0
fail() { echo "FAIL: $*"; fails=$((fails + 1)); }

GUIDE=guide/auto-input.md
CODES=guide/error-codes.json

# 1. The banned word, built from halves so this script passes its own check.
E1=integr E2=at R1=интег R2=рац
hits=$(LC_ALL=C.UTF-8 grep -rniIE "${E1}${E2}|${R1}${R2}" \
  --exclude-dir=.git --exclude-dir=build --exclude-dir=node_modules \
  --exclude-dir=.gradle --exclude-dir=results --exclude-dir=.superpowers . 2>/dev/null)
[ -n "$hits" ] && fail "banned word: $hits"

# 2. Code table parses; every code is in the guide.
if [ ! -f "$CODES" ]; then
  fail "$CODES missing"
elif ! node -e 'JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"))' "$CODES" 2>/dev/null; then
  fail "$CODES does not parse"
elif [ ! -f "$GUIDE" ]; then
  fail "$GUIDE missing"
else
  for code in $(node -e 'for (const e of JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"))) console.log(e.code)' "$CODES"); do
    grep -q -- "$code" "$GUIDE" || fail "code $code not in $GUIDE"
  done
fi

# 3-4. Methods, fields that no longer come back, the hold time, Retail only.
if [ ! -f "$GUIDE" ]; then fail "$GUIDE missing"; else
  for word in sendPreReceipt sendRefundPreReceipt getPreReceiptStatus registerSale registerSaleFull \
    registerRefund registerRefundFull getReceipt getReceiptByPayCheckNumber \
    fmNo kkmNo shiftNumber url fiscalSign 90 Retail; do
    grep -q -- "$word" "$GUIDE" || fail "'$word' not in $GUIDE"
  done
fi

# 5. Examples: parse, have title/request/answer, a valid envelope.
if ! ls guide/examples/*.json >/dev/null 2>&1; then
  fail "no guide/examples/*.json"
else
  for f in guide/examples/*.json; do
    problem=$(node -e '
      const e = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"));
      const p = [];
      for (const k of ["title", "request", "answer"]) if (!(k in e)) p.push("no " + k);
      const r = e.request || {};
      if (r.version !== "1.0.2") p.push("version is not 1.0.2");
      if (r.key !== "mock-kassa-key") p.push("key is not mock-kassa-key");
      if (!/^[0-9]+$/.test(String(r.id))) p.push("id is not digits");
      if (typeof r.id !== "string") p.push("id is not a string");
      if (!r.method) p.push("no method");
      console.log(p.join("; "));
    ' "$f" 2>&1) || problem="does not parse"
    [ -n "$problem" ] && fail "$f: $problem"
  done
fi

# 6. The plugin's copies equal the guide.
REF=plugins/mobilkassa/skills/auto-input/references
if [ -d "$REF" ]; then
  for name in auto-input.md error-codes.json; do
    cmp -s "guide/$name" "$REF/$name" || fail "$REF/$name differs from guide/$name (run sh tools/sync-guide.sh)"
  done
fi

[ "$fails" -eq 0 ] && echo "check-docs: OK" && exit 0
exit 1

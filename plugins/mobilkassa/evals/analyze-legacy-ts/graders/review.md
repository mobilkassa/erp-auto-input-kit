---
type: llm
focus:
  source: file
  path: mobilkassa-auto-input/analysis.md
---

PASS if the report lists, each with file and line, the calls to `registerSaleFull`, `registerRefundFull` (with `idRefundReceipt`) and `getReceiptByPayCheckNumber` in `src/kassa.ts`; the reads of `fiscalSign`, `fmNo`, `shiftNumber` and the QR printed from `url` (in `src/kassa.ts` and `src/receipt-print.ts`); flags the 30-second timeout and the sale sent without `payCheckNumber`; and lists `getXReport` in `src/report.ts` as unchanged.
FAIL if any of those is missing, or `getXReport` is listed as something to change.

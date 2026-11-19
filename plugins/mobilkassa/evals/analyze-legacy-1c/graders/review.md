---
type: llm
focus:
  source: file
  path: mobilkassa-auto-input/analysis.md
---

PASS if the report lists, each with file and line, the calls to `registerSaleFull`, `registerRefundFull` (with `idRefundReceipt`) and `getReceiptByPayCheckNumber`, and the uses of `fiscalSign`, `fmNo`, `shiftNumber` and the QR code printed from `url`; flags the 30-second timeout and the sale sent without `payCheckNumber`; lists `getXReport` as unchanged; and says the changes go into a configuration extension (расширение конфигурации).
FAIL if any of those is missing, if `getXReport` is listed as something to change, or if the report says nothing was found.

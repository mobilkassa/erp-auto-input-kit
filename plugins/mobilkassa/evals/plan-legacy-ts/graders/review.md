---
type: llm
focus:
  source: file
  path: mobilkassa-auto-input/plan.md
---

PASS if the plan has numbered steps that each name real files of this repo (`src/kassa.ts`, `src/receipt-print.ts`) and the S-scenarios (S1–S8) that prove them; moves sales to `sendPreReceipt` with the ERP's own `payCheckNumber`, refunds to `sendRefundPreReceipt`, and `getReceiptByPayCheckNumber` to `getPreReceiptStatus`; removes the QR and fiscal-sign handling; raises the 30-second timeout; uses the TypeScript reference client; leaves `src/report.ts` unchanged; and has a «Переход» section that mentions `ext-ser-mode = redirect`.
FAIL if any of those is missing or the plan changes `getXReport`.

---
type: llm
focus:
  source: file
  path: mobilkassa-auto-input/analysis.md
---

PASS if the report is a new connection (not "nothing to move"), names `closeSale` in `src/sales.ts` as the place to send `sendPreReceipt` and `registerReturn` in `src/returns.ts` as the place to send `sendRefundPreReceipt` with the sale number as `salePayCheckNumber`; maps the product `id` to `sku`; and says `ownerType` is missing from the product card.
FAIL if it says there is nothing to do, proposes a code change outside the report, or misses `ownerType`.

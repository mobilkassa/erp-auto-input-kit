---
name: auto-input
description: Mobilkassa / Pospoint kassa auto-input (pre-receipt) API on port 6461. Use when code talks to a Mobilkassa or Pospoint kassa over JSON-RPC on port 6461 or the /pospoint path, or names registerSale, registerSaleFull, registerRefund, registerRefundFull, getReceipt, getReceiptByPayCheckNumber, sendPreReceipt, sendRefundPreReceipt or getPreReceiptStatus — to read, write, review or migrate that code.
---

# Mobilkassa auto-input (pre-receipts)

The ERP sends the kassa a pre-receipt; the cashier checks it, may change it, and issues the receipt. The kassa prints it and sends it to the OFD. The ERP gets a status and the final composition, never fiscal data. Protocol 1.0.2, `POST http://<kassa>:6461/pospoint`, JSON in UTF-8, ≤ 2 MB.

Full guide (Russian, authoritative): [references/auto-input.md](references/auto-input.md). Code table: [references/error-codes.json](references/error-codes.json). Scenarios every migrated ERP must pass: [references/scenarios.md](references/scenarios.md).

## What changes on 1 December 2026

| Old method | After 1 December |
|---|---|
| `registerSale`, `registerSaleFull` | Pre-receipt under `ext-ser-mode = redirect` (every item must then carry `sku`, else `14804`; answer only after the cashier issues, fiscal fields `null`), else `-32601` |
| `registerRefund`, `registerRefundFull` | Same |
| `getReceipt`, `getReceiptByPayCheckNumber` | Always `-32601` |

Unchanged: `checkFmStatus`, `checkFmCapacity`, `getFmInfo`, `syncFmState`, `syncWithOfd`, `openZReport`, `getXReport`, `closeZReport`, `validateMarkCode`, `validateReceiptMarkCodes`, `validateMxikPackages`, `getMxikInfo`. Never touch these calls during a migration.

## Method map

- `registerSale` / `registerSaleFull` → `sendPreReceipt` with `type` `SALE` | `ADVANCE` | `CREDIT`; items keep the `registerSaleFull` fields (`name amount price mxik ownerType unit packageCode taxPercent`, optional `mkCode discount barcode comTin recipeId`) plus a required `sku` (the ERP's position key, ≤ 64 chars; missing or blank → `14804`). `byCash`/`byCard` optional; if given they must equal the total ±50 sum.
- `registerRefund` / `registerRefundFull` → `sendRefundPreReceipt` with `payCheckNumber` (refund document) and `salePayCheckNumber` or `saleId` (= old `idRefundReceipt`). `items` optional (none = whole remainder); each item has `amount` and names its sale line by `sku`, then `uid`, then `mxik`/`barcode` (+`name` tie-break), then `name`.
- `getReceipt` / `getReceiptByPayCheckNumber` → `getPreReceiptStatus` by `payCheckNumber`.

## Never returned (remove code that uses them)

Receipt number, `fmNo`, `kkmNo`, `shiftNumber`, fiscal receipt time, `url` (QR link), `fiscalSign`. The kassa prints the receipt. Any ERP code that prints the QR, stores the fiscal sign or reads these fields from a 6461 answer must go — also under `redirect`, where they arrive as `null`.

## Result

`{payCheckNumber, status}`; `status` is `WAITING` | `ISSUED` | `REJECTED`. `ISSUED` adds `type`, `changed` (cashier lowered, removed or paid another way), `items[]` (`sku name amount price discount sum`), `payments[]` (`type` `CASH` | `CARD` | `ONLINE` | `DEBT`, `amount`), `total`.

## The call is held

`sendPreReceipt` / `sendRefundPreReceipt` return only when the cashier issues (result) or rejects (`14828`). Untouched for 90 s, or moved to «Предчеки» → `14827` (not cancelled). Once the cashier started: no time limit. Repeats with the same `payCheckNumber` are safe: open → joins; in «Предчеки» → `14827`; issued → `14824`; after reject → accepted as new.

## Client pattern (every client must follow it)

1. Build the pre-receipt with the ERP's own `payCheckNumber` (never omit it).
2. Send with an HTTP timeout of 120 s.
3. `ISSUED` → store the composition and payments.
4. Timeout or `14827` → poll `getPreReceiptStatus` every 5 s (or tell the user the cashier is finishing).
5. `14828` → show «отклонён кассиром», allow a corrected re-send with the same number.

Envelope: `{"id":"<digits>","key":"<kassa key>","version":"1.0.2","method":"…","params":{…}}`. The answer's `id` is a number; errors of envelope/params validation answer `"id":0`.

## Reference clients

Ready code in `${CLAUDE_SKILL_DIR}/clients/<stack>/`: `1c` (BSL common module for a configuration extension), `ts` (Node 18+ / Electron main process), `java`. Each has a README. C# and PHP clients come in a later release: for those stacks follow «Шаблон клиента» in the guide, using the TS client as the model. Copy and adapt the client rather than writing a new one.

## Mock kassa

Test against the mock before a real kassa: `http://127.0.0.1:6461/pospoint`, key `mock-kassa-key`, cashier page `http://127.0.0.1:6461/`, control endpoints `/mock/state`, `/mock/{touch|park|open|issue|reject}`, `/mock/sales`, `/mock/reset`, `/mock/log` (see scenarios.md). Get it from https://github.com/mobilkassa/erp-auto-input-kit (`mock-kassa-1.0.2-windows.zip`, run `mock-kassa.exe`; or `cd mock-kassa && ./gradlew run` from a checkout). Check it with `GET /health` → `{"status":"UP"}`. If Pospoint itself runs on the same PC it holds port 6461: start the mock with `--port 6462`.

The mock checks the protocol, not the goods: MXIK against the tasnif catalog (`14825`), mark codes online and the FM are checked only by a real kassa. The final run is always on a real Pospoint kassa (Retail, «Автоввод данных чека» on, key from that settings block) with a test FM.

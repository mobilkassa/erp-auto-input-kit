---
name: analyze
description: Find every Mobilkassa 6461 receipt call and fiscal-field use in this ERP, or, for an ERP that does not talk to the kassa yet, the places where pre-receipts must be sent, and write mobilkassa-auto-input/analysis.md. Changes no code.
argument-hint: "[new] [path]"
disable-model-invocation: true
allowed-tools: Read Grep Glob Write
---

# /mobilkassa:analyze

Analyze the ERP in the path given in `$ARGUMENTS` (default: the current directory) for auto-input pre-receipts. Write one file, `mobilkassa-auto-input/analysis.md`, in Russian. Change nothing else: no source file, no config.

Two modes:

- **Переход** (default): the ERP issues receipts with the old 6461 calls. Sections 1–3.
- **Новое подключение**: `$ARGUMENTS` starts with `new`, or the search in section 2 finds no contact with the kassa at all (no `6461`, no `/pospoint`, no kassa method name). Sections 1, 2, 4 and 5.

An ERP that talks to the kassa but sends no receipts (only X/Z reports, FM checks) stays in **Переход**: the report says «Переносить нечего» and names `/mobilkassa:analyze new` for when the ERP should start sending receipts.

Read `${CLAUDE_PLUGIN_ROOT}/skills/auto-input/SKILL.md` first, and `${CLAUDE_PLUGIN_ROOT}/skills/auto-input/references/auto-input.md` for the field and code details.

## 1. Find the stack

- **1C**: a configuration dump (`Configuration.xml`, `*/Ext/*.bsl`, `CommonModules/`) or a 1C:EDT project (`src/**/*.bsl`, `.project`). If the repo looks like 1C but has no `.bsl` files (only `.cf`, `.cfe`, `.epf`, `.erf`, or nothing readable), stop and write in `analysis.md` how to export: Конфигуратор → Конфигурация → «Выгрузить конфигурацию в файлы» (or open the configuration in 1C:EDT), then run `/mobilkassa:analyze` again. Do not report "nothing found" for a closed binary configuration. Also say that the changes will go into a configuration extension, so for `/mobilkassa:implement` the extension must exist as files: in the Configurator create the extension (Конфигурация → Расширения конфигурации → Добавить), then Конфигурация → «Выгрузить конфигурацию в файлы» for that extension (or an extension project in 1C:EDT), and give the folder to the agent.
- **TypeScript / JavaScript / Electron**: `package.json`. **C#**: `*.csproj`, `*.sln`. **PHP**: `composer.json`, `*.php`. **Java / Kotlin**: `pom.xml`, `build.gradle*`. Anything else: name it and search the same way.

## 2. Search (Grep the whole tree; skip `node_modules`, `vendor`, `bin`, `obj`, `build`, `dist`, `.git`)

1. Calls to change, by method name in strings or identifiers: `registerSale`, `registerSaleFull`, `registerRefund`, `registerRefundFull`, `getReceipt`, `getReceiptByPayCheckNumber`. Already migrated: `sendPreReceipt`, `sendRefundPreReceipt`, `getPreReceiptStatus`.
2. Where the ERP talks to the kassa: `6461`, `/pospoint`, the HTTP client and its timeout (TS `AbortSignal.timeout`, `timeout:`; C# `HttpClient`, `.Timeout`; PHP `CURLOPT_TIMEOUT`, `max_execution_time`; Java `.timeout(`, `HttpClient`; 1C the 6th argument of `Новый HTTPСоединение(`). Any timeout below 120 s is a finding.
3. Fiscal fields read from a kassa answer: `fmNo`, `kkmNo`, `shiftNumber`, `fiscalSign`, `url` (QR link), receipt `id`/`time` kept as the fiscal receipt. Follow each one to where it is printed, stored or shown (QR printing, a fiscal-sign column or attribute, a slip template).
4. `idRefundReceipt` and where the ERP keeps the kassa's receipt id for refunds.
5. Sale calls whose params carry no `payCheckNumber`: a retry can make a second receipt. Sale items with no ERP position key: every item needs `sku` (`14804` without), in `sendPreReceipt` and also in `registerSale*` under `redirect` — find where the ERP's product id is and say it becomes `sku`.
6. Calls that stay as they are: `checkFmStatus`, `checkFmCapacity`, `getFmInfo`, `syncFmState`, `syncWithOfd`, `openZReport`, `getXReport`, `closeZReport`, `validateMarkCode`, `validateReceiptMarkCodes`, `validateMxikPackages`, `getMxikInfo`.

Read the files around every hit before you classify it: a hit in a comment or an unrelated `url` is not a finding.

## 3. Write `mobilkassa-auto-input/analysis.md`

Russian, in this shape:

```markdown
# Анализ: переход на автоввод данных чека

Режим: переход
Стек: <1С (выгрузка конфигурации) | TypeScript/Electron | C# | PHP | Java | …>
Клиент из плагина: <clients/1c | clients/ts | clients/java | нет готового — по руководству>

## Что нужно изменить

| Файл:строка | Что найдено | Что сделать |
|---|---|---|
| src/kassa.ts:44 | `registerSaleFull` без `payCheckNumber` | `sendPreReceipt`, свой номер документа — руководство, «sendPreReceipt» |

## Что остаётся без изменений

| Файл:строка | Метод |
|---|---|

## Риски

- <timeouts, places that wait for the fiscal receipt synchronously, UI that shows the QR, data migrations of stored fiscal signs>

## Итог

<N> мест для изменения, <M> без изменений. Следующий шаг: `/mobilkassa:plan`.
```

Every row has `file:line` (1-based, the line of the call or the field use). For 1C, the «Что сделать» column says the change goes into a configuration extension (расширение конфигурации), not the base configuration.

If nothing needs to change, say so plainly in «Итог» («Переносить нечего: …») and list the unchanged calls.

End by telling the user, in one or two lines in their language, how many places were found and that the next step is `/mobilkassa:plan`.

## 4. Новое подключение: find where pre-receipts go

Read `references/auto-input.md` sections «sendPreReceipt», «Удержание вызова», «sendRefundPreReceipt» first. Then find, reading the code around each place:

1. Where a sale is completed (posted, paid, closed): the place to send `sendPreReceipt`. TS / Java / C# / PHP: the service or handler that completes an invoice, order or check. 1C: the sale documents (`ЧекККМ`, `РеализацияТоваровУслуг`, `ОтчетОРозничныхПродажах` or the configuration's own) and their posting handler or form command.
2. Where a refund (return) is completed, and how it names its sale: this becomes `salePayCheckNumber`.
3. The document number: unique per sale and per refund, and kept across retries. It becomes `payCheckNumber`. A number made anew on each attempt is a finding.
4. Every required item field (`name`, `amount`, `price`, `mxik`, `ownerType`, `unit`, `packageCode`, `taxPercent`, `sku`): where the ERP keeps it (product card, document line). `sku` is the ERP's product or line key. A field the ERP does not have is a finding: name where it must be added.
5. Payment: if the ERP knows cash and card amounts, they become `byCash` / `byCard`; otherwise the cashier picks the payment.
6. Where settings live (the kassa address, key, timeout 120 s) and which HTTP client the project already uses.
7. Where the user sees a document's state: the states «ожидает кассира», «оформлен», «отклонён» go there.

## 5. Новое подключение: write `mobilkassa-auto-input/analysis.md`

Russian, in this shape:

```markdown
# Анализ: подключение автоввода данных чека

Режим: новое подключение
Стек: <…>
Клиент из плагина: <clients/1c | clients/ts | clients/java | нет готового — по руководству>

## Куда добавить

| Файл:строка | Что найдено | Что сделать |
|---|---|---|
| src/sales.ts:31 | `completeSale` проводит продажу | после проведения — `sendPreReceipt` с номером продажи, руководство «sendPreReceipt» |

## Данные позиции

| Поле | Откуда | Нет в учётной системе — что сделать |
|---|---|---|
| `sku` | `Product.id` | — |

## Риски

- <a call that holds until the cashier acts (90 s, or without limit once the cashier opens it) in a UI thread or a form; numbers that are not unique; missing item data>

## Итог

<N> мест, куда добавить отправку, <K> полей позиции, которых нет. Следующий шаг: `/mobilkassa:plan`.
```

Every row has `file:line`. For 1C, the «Что сделать» column says the change goes into a configuration extension, and section 1's export and extension steps apply here too.

End by telling the user, in one or two lines in their language, where sales and refunds will send pre-receipts, what item data is missing, and that the next step is `/mobilkassa:plan`.

---
name: analyze
description: Find every Mobilkassa 6461 receipt call and fiscal-field use in this ERP and write mobilkassa-auto-input/analysis.md. Changes no code.
argument-hint: "[path]"
disable-model-invocation: true
allowed-tools: Read Grep Glob Write
---

# /mobilkassa:analyze

Analyze the ERP in `$ARGUMENTS` (default: the current directory) for the move from the old Mobilkassa 6461 calls to auto-input pre-receipts. Write one file, `mobilkassa-auto-input/analysis.md`, in Russian. Change nothing else: no source file, no config.

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

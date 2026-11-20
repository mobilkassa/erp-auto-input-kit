---
name: plan
description: Write mobilkassa-auto-input/plan.md, the step-by-step move of this ERP from the old Mobilkassa 6461 calls to pre-receipts, or its first connection to them. Changes no code.
disable-model-invocation: true
allowed-tools: Read Grep Glob Write
---

# /mobilkassa:plan

Outside Claude Code (skills installed with `npx skills add`), `${CLAUDE_PLUGIN_ROOT}/skills/<name>/` is the folder `../<name>/` next to this skill, a `/mobilkassa:<name>` command is the skill `<name>`, and AskUserQuestion is asking the developer and waiting for the answer.

Write the plan for this ERP into `mobilkassa-auto-input/plan.md`, in Russian. Change no source file. The «Режим» line of `analysis.md` picks the plan: **переход** (the rules below as written) or **новое подключение** (see «Новое подключение»).

1. Read `mobilkassa-auto-input/analysis.md`. If it is missing, do the steps of `${CLAUDE_PLUGIN_ROOT}/skills/analyze/SKILL.md` first and write it.
2. Read `${CLAUDE_PLUGIN_ROOT}/skills/auto-input/SKILL.md`, `references/auto-input.md` and `references/scenarios.md` (S1–S8).
3. Pick the reference client for the stack from `${CLAUDE_PLUGIN_ROOT}/skills/auto-input/clients/` (`1c`, `ts`, `java`). With none for the stack, plan a client that follows the guide's «Шаблон клиента».
4. Read the code around every row of the analysis, so each step names real files and functions.

## Rules for the steps

- One step = one reviewable change: at most ~200 changed lines and a handful of files, and the code builds after it.
- Order: (1) add the client and its settings (address, key, timeout 120 s); (2) move sales to `sendPreReceipt` with the ERP's own `payCheckNumber` and `sku`, handling `issued` / `waiting` / `rejected` / `failed`; (3) the waiting path: a document state «ожидает кассира» and a status check through `getPreReceiptStatus`; (4) refunds to `sendRefundPreReceipt` (`salePayCheckNumber`, or `saleId` for sales stored with the old `idRefundReceipt`); (5) replace `getReceipt*` with `getPreReceiptStatus`; (6) remove printing and storing of fiscal fields (`fmNo`, `kkmNo`, `shiftNumber`, `url`, `fiscalSign`), keeping stored history read-only; (7) scenario tests against the mock kassa.
- For 1C: every change goes into a configuration extension (расширение конфигурации). Step 1 is "create the extension and dump it to files" (Конфигуратор → Расширения конфигурации → Добавить; «Выгрузить конфигурацию в файлы» for the extension, or an EDT extension project), done by the developer; the last 1C step is loading it back («Загрузить конфигурацию из файлов», «Обновить конфигурацию базы данных»). Send pre-receipts from a background job (`ДлительныеОперации` / `ФоновыеЗадания`), never straight from a form: the call holds until the cashier acts; name the extension objects (common module `МобилкассаАвтоввод`, borrowed documents, `&Вместо`/`&После` handlers).
- Новое подключение: the same rules with this order instead: (1) add the client and its settings; (2) only if «Данные позиции» lists missing fields: add them to the product card or document line; (3) send `sendPreReceipt` where a sale is completed, with the ERP's own `payCheckNumber` and `sku`, handling `issued` / `waiting` / `rejected` / `failed`; (4) the waiting path, as above; (5) refunds through `sendRefundPreReceipt` with `salePayCheckNumber`; (6) scenario tests against the mock kassa. No steps for old calls or fiscal fields. The plan's title is «План: подключение автоввода данных чека», and «Запуск» (shape below) replaces «Переход».
- Never plan a change to the unchanged methods (FM, X/Z reports, OFD sync, mark-code and MXIK checks).
- Each step lists the S-scenarios that prove it.

## plan.md shape

```markdown
# План: переход на автоввод данных чека

Стек: … · Клиент: … · Анализ: analysis.md

## Шаг 1. <название>
- [ ] Файлы: …
- Изменение: …
- Клиент: clients/<stack>/…
- Проверка: S1, S5 — <как: тест против тестовой кассы / вручную на странице кассира>

## Шаг 2. …

## Переход

1. Пока новая версия раскатывается, кассы организации работают в режиме `ext-ser-mode = redirect` (его включает Mobilkassa): касса принимает и старые вызовы, и предчеки, поэтому магазины переходят по одному. Старая версия работает в этом режиме, только если в каждой позиции `registerSale`/`registerSaleFull` есть `sku` (без него `14804`): если до перехода далеко, первым шагом добавьте `sku` в старые вызовы.
2. На каждой кассе: Retail, включить «Автоввод данных чека», взять адрес и ключ из этого блока.
3. Пройти S1–S8 на настоящей кассе с тестовым ФМ (см. acceptance.md после /mobilkassa:migrate).
4. После перевода всех магазинов — обычный режим автоввода; старые вызовы отвечают -32601.
```

For **новое подключение**, end the plan with this section instead of «Переход»:

```markdown
## Запуск

1. На каждой кассе: Retail, Настройки → Кассовый режим → включить «Автоввод данных чека», взять адрес и ключ из этого блока и внести их в настройки учётной системы.
2. Пройти S1–S7 на настоящей кассе с тестовым ФМ (см. acceptance.md после /mobilkassa:migrate).
```

End by asking the developer, in their language, to review `plan.md` before any code changes, and say that `/mobilkassa:implement` does one step at a time (or `/mobilkassa:migrate` the whole path with a stop after each step).

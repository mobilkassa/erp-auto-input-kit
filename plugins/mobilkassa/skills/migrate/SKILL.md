---
name: migrate
description: Move this ERP from the old Mobilkassa 6461 receipt calls to auto-input pre-receipts end to end — analyze, plan, implement step by step with a review stop after each, test against the mock kassa, and write the acceptance checklist.
disable-model-invocation: true
allowed-tools: Read Grep Glob Write Edit Bash AskUserQuestion
---

# /mobilkassa:migrate

The guided path from the old receipt calls (`registerSale*`, `registerRefund*`, `getReceipt*`) to pre-receipts. Talk to the user in their language. Everything written into the repo for this lives in `mobilkassa-auto-input/` (Russian), plus the code changes themselves.

Read `${CLAUDE_PLUGIN_ROOT}/skills/auto-input/SKILL.md` first.

## 1. Analyze

Do the steps of `${CLAUDE_PLUGIN_ROOT}/skills/analyze/SKILL.md` and write `mobilkassa-auto-input/analysis.md` (skip if it exists and is newer than the last commit touching the files it names). If it says there is nothing to move, tell the user and stop.

## 2. Plan

Do the steps of `${CLAUDE_PLUGIN_ROOT}/skills/plan/SKILL.md` and write `mobilkassa-auto-input/plan.md`.

## 3. Stop for the plan review

Show the step list (titles only) and ask the developer to review `mobilkassa-auto-input/plan.md` before any code change (AskUserQuestion: approve / change the plan / stop). Until the developer approves, change no source file — this run ends here when no answer comes.

## 4. Implement, one step at a time

For each step not marked `[x]`, follow `${CLAUDE_PLUGIN_ROOT}/skills/implement/SKILL.md` exactly: dirty-tree check, the change, the project's tests, the step's S-scenarios against the mock kassa, mark it done. After each step, stop and ask the developer to review the change before the next one (AskUserQuestion: next step / fix something / stop). A developer who stops can continue later with `/mobilkassa:implement` or `/mobilkassa:migrate`.

## 5. Full scenario run

When every step is done, run S1–S8 (`references/scenarios.md`) against the mock once more, all together. For 1C, walk the developer through them on the mock's cashier page and record each outcome. S8 is a search: no old receipt method name and no read of `fmNo`, `kkmNo`, `shiftNumber`, `url`, `fiscalSign` from a kassa answer is left in the code.

## 6. Acceptance checklist

Write `mobilkassa-auto-input/acceptance.md` (Russian) for the run on a real Pospoint kassa with a test FM:

- Подготовка: касса Retail; Настройки → Кассовый режим → «Автоввод данных чека» включён; адрес и ключ из этого блока внесены в настройки учётной системы; тестовый ФМ.
- S1–S8 as a checklist, each with what the cashier does on the real kassa («Оформить», change a quantity and the payment, «Отклонить», «В Предчеки») and what the ERP must show; a column for the result.
- Что проверяет только настоящая касса: ИКПУ по каталогу tasnif (14825), коды маркировки онлайн, ограничения продажи за наличные, печать чека.
- Порядок перехода from the plan's «Переход» section: `ext-ser-mode = redirect` while the new version rolls out — it serves the old calls only when every `registerSale*` item carries `sku` (`14804` without) — (ask Mobilkassa to switch it on for the organization), then each store moves to the new calls; after all stores, the old calls answer -32601.

## 7. Summary

Tell the developer what changed (files), what passed (tests, S1–S8), what is left (the acceptance run on a real kassa), and where the three files are.

---
name: implement
description: Do one step of mobilkassa-auto-input/plan.md (moving this ERP to Mobilkassa pre-receipts), test it against the mock kassa, mark it done, and stop for review.
argument-hint: "[step number]"
disable-model-invocation: true
allowed-tools: Read Grep Glob Write Edit Bash AskUserQuestion
---

# /mobilkassa:implement

Do exactly one step of `mobilkassa-auto-input/plan.md`: step `$ARGUMENTS`, or the first one not marked `[x]`. Talk to the user in their language; write code comments in the repo's language and style.

## Before you edit

1. No `mobilkassa-auto-input/plan.md` → tell the user to run `/mobilkassa:plan` (or `/mobilkassa:migrate`) and stop.
2. Run `git status --porcelain`. If files outside `mobilkassa-auto-input/` are changed, list them and ask (AskUserQuestion) whether to continue on top of them or stop so the developer can commit first. If git is not available, ask the same about uncommitted work.
3. Read the step, the files it names, `${CLAUDE_PLUGIN_ROOT}/skills/auto-input/SKILL.md`, and the sections of `references/auto-input.md` the step touches.

## Doing the step

- Use the reference client from `${CLAUDE_PLUGIN_ROOT}/skills/auto-input/clients/<stack>/`: copy it into the repo where the plan says and adapt names, settings and logging to the codebase. Do not write a second client next to it.
- Keep to the step: no refactoring the plan did not ask for, and never touch the unchanged methods (FM, X/Z reports, OFD sync, mark-code and MXIK checks).
- Always send the ERP's own `payCheckNumber`; map its line key to `sku`; handle all four outcomes (issued, waiting, rejected, failed).
- 1C: every change goes into the configuration extension named in the plan, and only into its dumped files. If the extension folder is not in the repo yet, stop and ask the developer for it: the extension must exist as files: in the Configurator create the extension (Конфигурация → Расширения конфигурации → Добавить), then Конфигурация → «Выгрузить конфигурацию в файлы» for that extension (or an extension project in 1C:EDT), and give the folder to the agent. Edit only `.bsl` module texts there; never hand-write the extension's XML metadata (new objects, borrowings) — list what the developer must add in the Configurator. End the step by telling the developer to load the extension back («Загрузить конфигурацию из файлов», «Обновить конфигурацию базы данных»).
- Never put the kassa's key in the code: read it from the ERP's settings or environment.

## Checking the step

1. Run the project's own build and tests (`npm test`, `dotnet test`, `gradle test` / `mvn test`, `vendor/bin/phpunit`). A failure you caused is fixed in this step.
2. Run the step's S-scenarios (`references/scenarios.md`) against the mock kassa:
   - Check it first: `curl -s http://127.0.0.1:6461/health` (or the port the developer uses). Down → tell the user how to start it — `mock-kassa.exe --idle-seconds 5` from `mock-kassa-1.0.2-windows.zip` (https://github.com/mobilkassa/erp-auto-input-kit), or `cd mock-kassa && ./gradlew run --args="--idle-seconds 5"` from a checkout; `--port 6462` if Pospoint holds 6461 — then ask (AskUserQuestion) whether it is running now. Never mark the step done without the scenario run.
   - TypeScript, Java, C#, PHP: put the scenarios in the repo's test suite as tests against the mock (model them on the reference client's test file; skip when the mock is down), drive the cashier through the mock's control endpoints, and run them.
   - 1C: no automated run exists. The 1C module runs on the 1C server: when that is another PC, the mock must run with `--host 0.0.0.0` and the ERP gets the LAN address it prints. Give the developer the exact S-steps for this step (what to do in 1C, what to do on the mock's cashier page `http://127.0.0.1:6461/`), then ask (AskUserQuestion) for the outcome of each.
3. All green → mark the step `- [x]` in `plan.md` and add one line under it: what changed and which scenarios passed.

## Stop

Show a short summary: files changed, tests and scenarios run with their result. Ask the developer to review the change (`git diff`) before the next step. Do not start the next step in the same run.

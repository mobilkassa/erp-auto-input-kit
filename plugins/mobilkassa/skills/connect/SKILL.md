---
name: connect
description: Connect this ERP to Mobilkassa auto-input pre-receipts for the first time, end to end — analyze where sales and refunds are completed, plan, implement step by step with a review stop after each, test against the mock kassa, and write the acceptance checklist.
disable-model-invocation: true
allowed-tools: Read Grep Glob Write Edit Bash AskUserQuestion
---

# /mobilkassa:connect

Outside Claude Code (skills installed with `npx skills add`), `${CLAUDE_PLUGIN_ROOT}/skills/<name>/` is the folder `../<name>/` next to this skill, a `/mobilkassa:<name>` command is the skill `<name>`, and AskUserQuestion is asking the developer and waiting for the answer.

The guided path for an ERP that does not send receipts to a Mobilkassa kassa yet. Follow `${CLAUDE_PLUGIN_ROOT}/skills/migrate/SKILL.md` exactly, in the mode **новое подключение**: run the analysis as `/mobilkassa:analyze new`, whatever the code already contains. Talk to the user in their language and never call the work a migration or a move: for this developer there are no old methods, only the kassa's pre-receipt methods.

---
type: llm
focus:
  source: file
  path: mobilkassa-auto-input/analysis.md
---

PASS if the report says there is nothing to move (no old receipt method and no fiscal field is used) and lists the `getXReport` call in `src/report.ts` as unchanged.
FAIL if it proposes any change to `src/report.ts`, or lists `getXReport` as something to replace.

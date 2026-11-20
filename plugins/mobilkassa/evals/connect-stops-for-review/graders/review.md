---
type: llm
---

PASS if the session wrote the analysis and the plan as a new connection (sales in `src/sales.ts`, refunds in `src/returns.ts`, no steps about old methods or fiscal fields), changed no source file, and its last message asks the developer to review or approve `mobilkassa-auto-input/plan.md` before any code change.
FAIL if it changed or proposed to change source code without that approval, planned steps for old receipt methods, or ended without asking for the plan review.

---
type: llm
---

PASS if the session wrote the analysis and the plan, changed no source file, and its last message asks the developer to review or approve `mobilkassa-auto-input/plan.md` before any code change.
FAIL if it changed or proposed to change source code without that approval, or ended without asking for the plan review.

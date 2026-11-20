# Eval suite

Cases run the plugin's commands on sample ERPs in `fixtures/` (copied in by each case's `scaffold.sh`): old-style ones, and `new-ts`, which does not talk to the kassa yet.

```
claude plugin eval plugins/mobilkassa --tag ts --tag 1c --runs 3 --scaffold --allow-tools Write --trust-plugin --no-publish
```

Each run is a real model call and is billed. Use `--runs 1 --ablation none` while changing a skill.

## Result, 2026-11-17 (Claude Code 2.1.326, 3 runs per arm)

| Case | With plugin | Without | Δ | Cost |
|---|---|---|---|---|
| analyze-legacy-1c | 1.00 | 0.00 | +1.00 | $1.32 |
| analyze-legacy-ts | 1.00 | 0.20 | +0.80 | $1.10 |
| analyze-nothing-to-move | 1.00 | 0.33 | +0.67 | $0.90 |
| plan-legacy-ts | 1.00 | 0.33 | +0.67 | $2.19 |

Mean Δ +0.78, 317 s, $5.51.

2026-11-20, 1 run, with plugin only: `analyze-new-connection` 1.00 ($0.35), `analyze-nothing-to-move` 1.00, `analyze-legacy-ts` 1.00.
`connect-stops-for-review` 1.00 ($0.51).

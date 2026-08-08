# Architecture documentation

These docs describe how the game **currently works**. They are reference material, not a
changelog — there is no "this supersedes that" to untangle. Git history is the history.

If you are an AI assistant, start with [`../ai/manifest.yml`](../ai/manifest.yml): it maps
keywords and task shapes to the docs worth loading, so you can read two files instead of nine.

| Doc | Covers |
| --- | --- |
| [overview.md](overview.md) | Codebase map, key classes, conventions, where to change what |
| [decisions.md](decisions.md) | Upstream design choices to respect + scaffolding decisions, with diagrams |
| [combat.md](combat.md) | Rank-based combat: strength tiers, positional defense, capture, merging |
| [units-and-buildings.md](units-and-buildings.md) | Obj ids, unit/building stats, trees, graves, movement |
| [economy.md](economy.md) | Income, taxes/upkeep, bankruptcy, farm price scaling, Ruleset formulas |
| [turn-cycle.md](turn-cycle.md) | The exact ordered sequence of a turn, snapshots/undo, match end |
| [ui.md](ui.md) | Hand-rolled UI: scenes, buttons, in-game HUD, rendering, animations |
| [persistence.md](persistence.md) | Save/level-code encodings, preferences, replays, append-only rules |
| [ai-opponent.md](ai-opponent.md) | The AI difficulty ladder and everything it does not understand |

Build and run instructions live in [`RUNNING.md`](../../RUNNING.md).

## What this repo is

Essentially upstream `yiotro/antiyoy` (including diplomacy and fog of war) — despite the
repo name, no ships/ports/naval code exists yet. What this AI-dev setup adds on top of the
upstream source:

- **Desktop gradle scaffolding** (`desktop/` module, root `build.gradle`, `Makefile`) so the
  game compiles and runs without the gdx-setup tool.
- **`VerifyHarness`** — a scripted runtime harness for hands-off verification.
- **These docs** plus the AI routing configuration in [`../ai/`](../ai/README.md).

## Conventions in these docs

- Every number quoted here lives in `gameplay/rules/GameRules.java` or the `Ruleset`
  classes. If a doc and the code disagree, **the code is right** — fix the doc.
- Each doc ends with the manual checks worth running for changes in its area. There is no
  test framework; `make run` and the level editor are the harness.
- "Known gaps" sections describe accepted limitations, not open bugs.

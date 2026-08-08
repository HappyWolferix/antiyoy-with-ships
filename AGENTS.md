# AGENTS.md — antiyoy-with-ships

Turn-based hex strategy game, libGDX / Java. A fork of `yiotro/antiyoy`. Despite the repo
name, there is **no naval layer yet** — this is essentially upstream Antiyoy (rank-based
combat, diplomacy, fog of war). This file is the canonical entry point for all coding
agents; the AI configuration lives in `docs/ai/`.

## How to load context (do this literally)

1. Read this file.
2. Match the task against **[`docs/ai/manifest.yml`](docs/ai/manifest.yml)** — the single
   routing manifest. Its `keywords`, `task_routing` and `folders` sections tell you exactly
   which two or three docs to read. No broad codebase search should be needed; crawl source
   beyond the files you are changing only if the user explicitly asks for it.
3. Load the `AGENTS.md` overlay of every folder on the path to the files you will touch
   (`core/`, `core/src/yio/tro/antiyoy/{gameplay,ai,menu}/`, `desktop/`, `tools/`,
   `assets/`). Overlays add local rules; they never override this file.
4. The always-applied rules are in `docs/ai/rules/`: `code-style.md`, `boundaries.md`,
   `verification.md`, `documentation.md`.
5. Start with [`docs/architecture/overview.md`](docs/architecture/overview.md) for anything
   non-trivial; [`docs/architecture/decisions.md`](docs/architecture/decisions.md) explains
   why things are the way they are.

## Build and run

```sh
make run
```

Full setup, JDK requirements and known warnings: [`RUNNING.md`](RUNNING.md). All commands:
[`docs/ai/references/command-matrix.md`](docs/ai/references/command-matrix.md). Unlike
upstream, this repo has the desktop scaffolding committed — no `gdx-setup` step is needed.

## Feedback loops — verify before you finish

Run every loop that matches your change, cheapest first
(details: [`docs/ai/rules/verification.md`](docs/ai/rules/verification.md)):

| Loop | Command | When |
| ---- | ------- | ---- |
| Docs validation | `make validate-ai-docs` | any docs/ or AGENTS.md change |
| Compile (also enforces Java 6) | `make build` | any Java change |
| Tools/sprites | `make sprites` | tools/ or sprite png changes |
| Runtime smoke | `make run` + level editor | any behaviour change |
| Spec conformance | reread the matched `docs/architecture/*` doc | any behaviour change |

There is no unit-test framework; the level editor is the fastest way to build a specific
board, and `desktop/`'s `VerifyHarness` scripts taps/screenshots for hands-off checks.

Deliver complete, verified changes. If a loop fails for reasons outside your change, or a
requirement is ambiguous, **ask the user** instead of guessing.

## Non-negotiables

- **Java 6 source level.** No lambdas, no streams, no `var`.
- **All tunable numbers go in `gameplay/rules/GameRules.java`.** Grep it before hardcoding
  one. Income/upkeep formulas live in `gameplay/rules/RulesetGeneric.java`.
- **Persistence is append-only.** Never add Java serialization. `Obj` ids are append-only —
  renumbering breaks every save and level code. See
  [`docs/architecture/persistence.md`](docs/architecture/persistence.md).
- **Logging is `System.out.println`.** Do not introduce a framework.
- **libGDX stays at 1.9.10** (1.9.11 breaks `InputProcessor.scrolled`).
- Full list: [`docs/ai/rules/boundaries.md`](docs/ai/rules/boundaries.md).

## Fork rules (differences from upstream)

- **Units cannot spawn in enemy territory.** `FieldManager.buildUnit` only accepts hexes of
  the buying province. A freshly bought unit can move in the same turn, so attacking with
  fresh recruits means buying near the border and moving — reach limited to one move zone.
  The AI buys next to its target and attacks immediately (`findHexToStageUnit` +
  `checkToAttackFromStagingHex` in `ArtificialIntelligence` and `AiMaster`).

## Two traps worth knowing before you start

- **Combat has no HP.** Capture legality is `RulesetGeneric.canUnitAttackHex`: strength 4
  beats everything; otherwise the attacker's strength must exceed
  `Hex.getDefenseNumber()`. A successful attack kills instantly. See
  [`docs/architecture/combat.md`](docs/architecture/combat.md).
- **`strength` doubles as a tier id.** Merging caps and AI unit-building are arithmetic
  over the 1..4 range — changing those numbers ripples through combat, economy and AI at
  once.

More gotchas are listed in the manifest's `gotchas` section.

## Keeping the docs true

`docs/architecture/` describes current behaviour, with no supersede chains. If you change
behaviour, update the relevant doc in the same commit and add keywords to
`docs/ai/manifest.yml` if you introduced a new concept. If a doc and the code disagree, the
code is right — fix the doc. Then run `make validate-ai-docs`.

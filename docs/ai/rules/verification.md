# Verification — the feedback-loop ladder

There is no unit-test framework in this repo (`gameplay/tests/` are in-game debug screens).
Verification is a ladder of feedback loops, cheapest first. Run every loop whose trigger
matches your change; stop and fix on the first failure before moving up.

| # | Loop | Command | Run when | Catches |
| - | ---- | ------- | -------- | ------- |
| 1 | Docs validation | `make validate-ai-docs` | anything under `docs/` or any `AGENTS.md` changed | broken manifest paths, unregistered overlays, dead doc links |
| 2 | Compilation | `make build` | any Java change | syntax errors, and Java-6 violations (lambdas, streams, `var`) |
| 3 | Tools/sprites | `make sprites` | changes under `tools/` or to sprite pngs | tool compile errors, atlas regeneration failures |
| 4 | Runtime smoke | `make run` | any behaviour change | boot failures, obvious visual/interaction breakage |
| 5 | Spec conformance | manual | any behaviour change | divergence from `docs/architecture/*` |

## How to run loop 4 well

`make run` launches the desktop game. The **level editor is the fastest harness**: build the
exact board that exercises your change (specific units, towers, province shapes), then play
it. Each `docs/architecture/*.md` doc ends with the manual checks worth running for changes
in its area — treat that list as the test plan.

For scripted verification without a human at the keyboard, the desktop module includes
`VerifyHarness` (`./gradlew :desktop:run -PmainClass=yio.tro.antiyoy.desktop.VerifyHarness
-PharnessDir=<dir>`): it runs the real game, takes taps/keys from `<dir>/cmd.txt`, writes
acks and state dumps to `<dir>/ack.txt`, and captures screenshots from the framebuffer.

## How to run loop 5

The docs in `docs/architecture/` describe **current behaviour** and are the spec. After a
behaviour change, reread the docs matched by `docs/ai/manifest.yml` for your task:

- If your change matches the doc — done.
- If it doesn't, either your change is wrong, or the doc must be updated **in the same
  commit** (and new concepts added to the manifest keywords).

## Choosing the depth

- Documentation-only change: loop 1.
- Refactor with no behaviour change: loops 2 and 4 (boot only).
- Gameplay/balance/UI change: loops 2, 4 (with an editor-built board), 5.
- Persistence change: loops 2, 4, 5 — plus a save/load round-trip of an old save if one exists.

## When blocked

If a loop fails for a reason outside your change, or a requirement is ambiguous, **stop and
ask the user** instead of guessing. Deliver either a fully verified change or a precise
question — never a "probably works" change.

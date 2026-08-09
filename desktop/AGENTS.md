# desktop/ — overlay

Desktop launcher scaffolding, an addition of this AI-dev setup (upstream ships only
`core/` + `assets/`). Contains the LWJGL3 launcher (`DesktopLauncher`), the scripted
runtime harness (`VerifyHarness`), the batch AI harness (`AiSkirmishHarness`), and the gradle
wiring used by `make run` / `make build`.

Local rules:

- Keep this module thin: window setup, launching `YioGdxGame`, and the harnesses — no game
  logic. Harnesses may read game state and drive the loop, but must not encode rules.
- Both harnesses are selected with `-PmainClass=…` and take `-PappArgs="a,b,c"`; neither is
  part of the shipped game.
- libGDX version is pinned to 1.9.10 in the root `build.gradle`; do not bump (1.9.11's
  `InputProcessor.scrolled()` float signature breaks compilation).
- JDK discovery quirks and known launch warnings are documented in `RUNNING.md` — check it
  before "fixing" a warning.

Verify: `make run`.

# core/ — overlay

The whole game lives here; source root is `core/src/yio/tro/antiyoy/`, entry class
`YioGdxGame` (libGDX `ApplicationAdapter`: render loop, input dispatch, top-level wiring).

Load first: root `AGENTS.md`, then `docs/architecture/overview.md` for the package and
class map. Deeper overlays exist for `gameplay/`, `ai/`, and `menu/` — load the one on the
path to your files.

Local rules on top of the root ones:

- Java 6 source level applies to everything under this tree; `make build` enforces it.
- `stuff/` (pooling, fonts, scroll engines, `LanguagesManager`) and `factor_yio/`
  (`FactorYio` animatable float) are shared infrastructure — reuse before writing new
  utilities.
- Top-level classes here (SettingsManager, SoundManagerYio, KeyboardManager, …) are
  app-level singletons wired from `YioGdxGame`; follow that wiring pattern for new ones.

Verify with `make build`, then `make run` (see `docs/ai/rules/verification.md`).

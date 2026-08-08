# Code style

- **Java 6 source level.** No lambdas, no streams, no `var`, no try-with-resources
  assumptions beyond what already compiles. `make build` is the enforcement — it fails on
  newer syntax.
- **All tunable numbers go in `core/src/yio/tro/antiyoy/gameplay/rules/GameRules.java`.**
  Grep it before hardcoding a constant anywhere else. Income/upkeep formulas live in
  `rules/RulesetGeneric.java` / `rules/RulesetSlay.java`.
- **Logging is bare `System.out.println`.** Do not introduce a logging framework.
- Classes suffixed `Yio` are the original author's namespace, not a framework — follow the
  suffix only when extending an existing `*Yio` family.
- Match the surrounding code's idiom: field-injection-style manager objects, explicit loops,
  object pooling via `stuff/` where hot paths already use it.
- Animation state uses `FactorYio` (an animatable float with easing) — reuse it, don't
  hand-roll timers.
- No new third-party dependencies without explicit user approval; libGDX is pinned to 1.9.10.

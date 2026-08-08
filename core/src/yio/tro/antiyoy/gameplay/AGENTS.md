# gameplay/ — overlay

Simulation core: field, hexes, provinces, units, turn cycle, rules, save formats, replays,
editor, campaign, diplomacy, fog of war. The docs are the spec — match your task in
`docs/ai/manifest.yml` and read the routed `docs/architecture/*` docs before opening source.

Key subpackages: `rules/` (GameRules constants + Ruleset implementations), `data_storage/`
(save/level-code encoders), `replays/` (replay recording/playback), `game_view/` (match
rendering), `touch_mode/` (input modes; `TmDefault` is normal play), `diplomacy/`,
`fog_of_war/`, `editor/`, `tests/` (in-game debug screens, NOT unit tests).

Local rules:

- **Every tunable number goes in `rules/GameRules.java`.** No exceptions. Income/upkeep
  formulas live in `rules/RulesetGeneric.java` (and `RulesetSlay.java` for slay rules).
- `Obj` ids and all persistence formats are **append-only** — read
  `docs/architecture/persistence.md` before touching `data_storage/` or `SavableYio`
  implementors.
- Combat legality is positional: `Hex.getDefenseNumber()` plus the checks in
  `MoveZoneDetection` decide what can be captured. There is no HP system — a successful
  attack replaces the defender. See `docs/architecture/combat.md`.
- The turn sequence is documented in `docs/architecture/turn-cycle.md` — keep code and doc
  in sync.

Verify: `make build`, then `make run` with a level-editor board that exercises the change,
then the closing checklist of the matching `docs/architecture/*` doc.

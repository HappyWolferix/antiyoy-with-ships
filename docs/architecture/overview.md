# Architecture

Antiyoy is a turn-based hex strategy game built on libGDX. This repo is essentially
vanilla upstream antiyoy: rank-based combat (unit strength 1–4 vs. hex defense), no
HP system, no naval layer.

- **Source root**: `core/src/yio/tro/antiyoy/`
- **Size**: ~1,030 Java files, Java 6-era code
- **Run**: `make run` (see [RUNNING.md](../../RUNNING.md))

## Package map

| Package | What lives there |
| --- | --- |
| `gameplay/` | Simulation core: `FieldManager`, `Hex`, `Province`, `Unit`, `GameController`, map generation, editor, campaign |
| `gameplay/rules/` | `GameRules` constants + `Ruleset` implementations (`RulesetGeneric` / `RulesetSlay`) |
| `gameplay/data_storage/` | Save/load and level codes: `GameSaver`, `EncodeManager`, `DecodeManager`, legacy import/export |
| `gameplay/diplomacy/` | `DiplomacyManager`, contracts, debts, `DiplomaticAI` |
| `gameplay/fog_of_war/` | `FogOfWarManager` and slice-based visibility |
| `gameplay/replays/` | Replay recording and playback |
| `gameplay/game_view/` | Match rendering (`GameView`, `Render*` classes) |
| `gameplay/touch_mode/` | Input modes (`TmDefault` is normal play, `TmEditor` for the editor) |
| `gameplay/tests/` | In-game debug screens, **not** a unit test suite |
| `ai/`, `ai/master/` | AI difficulty ladder; `AiMaster` is the strongest |
| `menu/` | All UI — scenes (`menu/scenes/`), widgets, renderers. Hand-rolled, no Scene2D widgets |
| `stuff/` | Shared utilities: `Yio`, `GraphicsYio`, `PointYio`, object pooling, fonts, scroll engines |
| `factor_yio/` | `FactorYio`, an animatable float with easing behaviors; used everywhere for animation state |
| `desktop/src/` (module) | `DesktopLauncher` — scaffolding added by this setup, not upstream |

## The classes that matter

| Class | Role |
| --- | --- |
| `YioGdxGame` | libGDX `ApplicationAdapter` + `InputProcessor`. Render loop, input dispatch, top-level wiring |
| `gameplay/GameController` | Orchestrates a match: turn order (`turnStartActions`/`endTurnActions`), moves, snapshots, win conditions |
| `gameplay/FieldManager` | Owns the hex grid (`Hex field[][]`, `activeHexes`), map generation, province detection; holds `moveZoneManager`, `diplomacyManager`, `fogOfWarManager`, `massMarchManager` |
| `gameplay/Hex` | One tile: `fraction`, `objectInside` (an `Obj` id), unit, adjacency, `getDefenseNumber()` |
| `gameplay/Province` | A connected group of same-fraction hexes: `money`, `getIncome()`, `getTaxes()`, `getProfit()` |
| `gameplay/Unit` | One unit: `strength` (1–4), `moveFactor` (a `FactorYio`), current/last hex |
| `gameplay/Obj` | Static int ids for hex contents: `PINE`, `PALM`, `TOWN`, `TOWER`, `GRAVE`, `FARM`, `STRONG_TOWER`. Append-only |
| `gameplay/rules/GameRules` | Tunable numbers (prices, taxes, `UNIT_MOVE_LIMIT`) plus static mode flags (`slayRules`, `fogOfWarEnabled`, ...) |
| `gameplay/rules/Ruleset` | Abstract rule hooks: `canUnitAttackHex`, `getHexIncome`, `getUnitTax`, `canMergeUnits`, ... |
| `gameplay/MoveZoneDetection` | Computes where a selected unit may go — shared by player and AI |
| `gameplay/SelectionManager` | Player selection, build tips, tap handling |
| `ai/AiFactory` | `createAiList(difficulty)` picks the AI class per difficulty and ruleset |
| `ai/master/AiMaster` | Strongest AI; delegates to `AttackManager` / `DefenseManager` |
| `menu/MenuControllerYio` | UI control flow; scenes extend `menu/scenes/AbstractScene` |

## Combat model

Rank-based, not HP-based. `RulesetGeneric.canUnitAttackHex(int unitStrength, Hex hex)`
is the whole rule: a strength-4 unit captures anything; otherwise the attacker's
`strength` must exceed `Hex.getDefenseNumber()`, which derives from the hex's own
object/unit and its neighbors. Nothing takes damage — captures are all-or-nothing.

## Where to change what

| Goal | Start here |
| --- | --- |
| Retune a price, tax or income number | `gameplay/rules/GameRules.java` |
| Change what an attack may capture | `RulesetGeneric.canUnitAttackHex` + `Hex.getDefenseNumber` |
| Change income or upkeep formulas | `gameplay/rules/RulesetGeneric.java` (`getHexIncome`, `getUnitTax`) |
| Add a building | `Obj` (append only) + `Hex.getDefenseNumber` + `FieldManager` build methods + save codecs |
| Change turn ordering | `GameController.turnStartActions` / `endTurnActions` |
| Change unit movement range | `GameRules.UNIT_MOVE_LIMIT` + `MoveZoneDetection` |
| Change AI behavior | `ai/AiFactory.createAiList` picks the class; `ai/master/AiMaster` for the top tier |
| Change match rendering | `gameplay/game_view/GameRender*` classes, registered in `GameRendersList` |
| Change menus | `menu/scenes/Scene*` + `menu/MenuControllerYio` |
| Save format | `gameplay/data_storage/` (`GameSaver`, `EncodeManager`/`DecodeManager`) |
| Window size / desktop launch | `desktop/src/yio/tro/antiyoy/desktop/DesktopLauncher.java` |

## Conventions

- **Java 6-era source.** No lambdas, no streams. The build compiles at Java 8
  level (see [decisions.md](decisions.md)), but match the surrounding style.
- **`Yio` suffix** is the original author's namespace convention, not a framework.
- **Logging** is bare `System.out.println` (~240 sites). Do not introduce a framework.
- **Animation** is `factor_yio/FactorYio`: an eased float driven by `move()` each
  frame, started with `appear(moveMode, speed)`, read with `get()`.
- **Object pooling** in hot paths via `stuff/object_pool/` (`ObjectPoolYio`,
  `ReusableYio`); pooled objects implement `reset()`.
- **Persistence** is hand-rolled string encoding (`EncodeableYio`,
  `LegacyExportManager.getFullLevelString`), stored via libGDX `Preferences` in
  `GameSaver`. Extend append-only; never add Java serialization.
- **Localization** is one file: `assets/languages.xml`, loaded by `CustomLanguageLoader`.
- **Tight coupling warning**: `Hex`, `Province` and `FieldManager` share province
  membership, adjacency and per-hex state. Change them together.

## Build / run

The upstream repo ships only `core/src` + `assets`; the Gradle root files, the
`desktop` module and the `Makefile` are scaffolding added by this setup
([RUNNING.md](../../RUNNING.md)).

```sh
make build   # compile
make run     # launch (gradle :desktop:run, working dir = assets/)
```

libGDX is pinned to **1.9.10** and the root `build.gradle` overrides upstream's
Java 1.6 `sourceCompatibility` to 8 — see [decisions.md](decisions.md) before
touching either.

There is no test framework and no CI; verification is running the game. The level
editor is the fastest way to construct a specific board state.

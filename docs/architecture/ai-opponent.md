# AI

All AI code lives in `core/src/yio/tro/antiyoy/ai/` and `ai/master/`. `ai/AiFactory` picks the
implementation per fraction by difficulty (`ai/Difficulty` constants `EASY=0 … MASTER=5`):

| Difficulty | Generic rules | Slay rules |
| --- | --- | --- |
| Easy | `AiEasy` | `AiEasy` (same class) |
| Normal | `AiNormalGenericRules` | `AiNormalSlayRules` |
| Hard | `AiHardGenericRules` | `AiHardSlayRules` |
| Expert | `AiExpertGenericRules` | `AiExpertSlayRules` |
| Balancer | `AiBalancerGenericRules` | `AiBalancerSlayRules` |
| Master | `ai/master/AiMaster` | `AiExpertSlayRules` (fallback — no slay Master exists) |

`AiFactory.createAiList(difficulty)` (or `createCustomAiList(int[])` for per-fraction
difficulties) fills `GameController.aiList` with one `AbstractAi` per fraction — human
fractions get an AI object too, it just never runs on their turn.

Class hierarchy: `AbstractAi` (holds `gameController` + `fraction`, abstract `perform()`)
→ `ArtificialIntelligence` (all shared heuristics, abstract `makeMove()`)
→ `ArtificialIntelligenceGeneric` (adds farm building, `spendMoney()` = towers → farms → units)
→ the difficulty classes. `AiMaster` extends `AbstractAi` directly and shares nothing with the
ladder. `AiRestoredBalancerGeneric` / `AiRestoredBalancerSlay` also extend `AbstractAi` but are
**dead code** — nothing instantiates them, not even `AiFactory`.

## When the AI runs

`GameController.checkForAiToMove()` fires from the render/move loop when it is not a player
turn (and not replay mode): `performAiMove()` calls `aiList.get(turn).perform()` **once**,
then immediately `applyReadyToEndTurn()`. So an AI gets exactly one synchronous `perform()`
call per turn and must do everything — move units, spend money, merge — inside it. See
[turn-cycle.md](turn-cycle.md).

## The ladder (`ArtificialIntelligence` and subclasses)

`ArtificialIntelligence.perform()` resets `numberOfUnitsBuiltThisTurn` and calls the subclass
`makeMove()`. The typical shape is:

1. `moveUnits()` — collect every `unit.isReadyToMove()` in owned provinces, compute its move
   zone via `GameController.detectMoveZone(hex, strength, GameRules.UNIT_MOVE_LIMIT)`, strip
   friendly units/buildings, and call `decideAboutUnit(unit, moveZone, province)`.
2. `spendMoneyAndMergeUnits()` — per owned province: `spendMoney(province)` (towers, farms in
   generic rules, then units) and `mergeUnits(province)`.
3. Hard/Expert/Balancer also run `moveAfkUnits()` — units stuck in provinces larger than 20
   hexes get pushed toward the perimeter (Expert uses
   `MassMarchManager.performForSingleUnit`, others move randomly).

Key shared heuristics in `ArtificialIntelligence`:

- `decideAboutUnit` — priority order: clean own palms (strength ≤ 2), attack the best hex from
  `findAttackableHexes`, else clean own pines, else `pushUnitToBetterDefense`.
- `findMostAttractiveHex` — barons/knights (strength 3–4) prefer enemy towers via
  `findHexAttractiveToBaron`; otherwise maximize `getAttackAllure` (count of own adjacent
  hexes, i.e. prefer cutting into the enemy where it borders friendly land).
- `tryToBuildUnits` — loops strength `i = 1..4`, gated by `Province.canAiAffordUnit(i)`
  (`money + (strength+1) * predictedProfit >= 0`), buying attackers via
  `tryToAttackWithStrength`. Because units cannot spawn in enemy territory, the new unit is
  bought on an own hex next to the chosen target (`findHexToStageUnit`) and immediately
  attacks from there (`checkToAttackFromStagingHex`); if no adjacent own hex is free, it
  stays staged for the next turn. A kick-start rule always buys a peasant if the province
  has ≤ 1 unit.
- `needTowerOnHex` — build a tower where `getPredictedDefenseGainByNewTower(hex) >= 5`
  (number of newly-defended own hexes minus adjacent existing towers).
- `isAllowedToBuildNewUnit` — only limits builds when diplomacy is on and humans are playing:
  max `min(max(3, hexCount/4), 10)` new units per turn per province.

Per-difficulty deltas:

- **`AiEasy`** — moves units *randomly* (only tree-cleaning is purposeful), never builds
  towers, builds units inside the province instead of attacking with them, only merges
  peasant+peasant and only with 25% probability per province.
- **Normal** — skips `decideAboutUnit` for each unit with 50% probability (`checkChance(0.5)`);
  builds units inside the province, not on attack targets.
- **Hard** — full attack logic; upgrades to strong towers whenever it can afford them.
- **Expert** (`AiExpertGenericRules`) — adds `unitCanMoveSafely` (do not leave > 3 undefended
  perimeter hexes behind), lowers the tower threshold to gain ≥ 4, builds strong towers only
  where a nearby province is at least half its own size (`needsStrongTowerOnHex`), and its
  `tryToBuildUnits` restarts the strength loop after every successful attack.
- **Balancer** (`AiBalancerGenericRules`, extends Expert) — sorts attack targets so the
  leading player is attacked first (`Comparator<Hex>` on `fieldManager.getPlayerHexCount()`),
  reinforces units that face enemies but cannot capture anything
  (`tryToReinforceUnits`/`unitHasToBeReinforced`), deliberately wastes money on peasants when
  its own army idles (`checkToKillRedundantUnits`), doubles attack allure for enemy farms and +5
  for enemy towns, and only builds towers on the front line. The slay variants
  (`AiExpertSlayRules`, `AiBalancerSlayRules`) mirror this without farms/strong towers.
- **Balancer province modes** (`BalancerBrain`, `ProvinceMode`) — once per turn each of the
  balancer's provinces is classified EXPAND (free land nearby), TURTLE (a neighbour is stronger,
  or the frontier it faces is fortified) or ECONOMY. The mode re-orders spending (EXPAND defers
  towers) and biases attacks toward neutral land; TURTLE additionally demands an 8-turn income
  runway instead of 5 before buying a unit. Modes deliberately never switch a category of
  spending off — earlier versions that did cost about two thirds of the AI's territory. The cache
  is keyed on `Province` objects, which are rebuilt every turn, so it is cleared in `makeMove()`.
  Measurements and rejected variants:
  [implementation-plans/done/adaptive-balancer-ai.md](../ai/implementation-plans/done/adaptive-balancer-ai.md).

## `AiMaster` (`ai/master/`)

The strongest AI, structured as a small utility system rather than a rule cascade.

- **`AiData`** — a per-hex scratchpad hung off every `Hex` (`hex.aiData`): `loneliness` and
  `attractiveness` (computed once at load in `AiMaster.prepare()` via BFS over the whole map),
  plus per-turn fields (`firstLine`/`secondLine`, `importance`, `solidDefense`, `tastiness`,
  `vicinity`, `dependentUnits`, `referenceHex`, …) reset each turn. Because `aiData` is shared
  board-wide, every `AiMaster` recomputes it at the start of its own processing.
- **`PropagationCaster`** — abstract BFS helper with overridable `isPropagationAllowed(src, dst)`
  and `onHexReached(prev, hex)`; used for loneliness, attractiveness, importance decay from
  farms, path-to-capital indexing, army presence, etc.
- **`MasterAction` / `MaType`** — turn-level strategies: `cut_tree`, `peacefully_expand`,
  `attack`, `defend`.
- **`PossibleSpending` / `PsType`** — money sinks: `unit1..unit4`, `farm`, `tower1` (normal),
  `tower2` (strong).
- **`AttackManager`** — picks the "most tasteful" enemy hex/province, computes first/second
  attack lines, gathers ready units into a ready area near an entry hex, ensures the path back
  to the capital is covered (`ensureCover`, buying cover units if needed) before committing,
  then applies an attack pattern. If it cannot cover, it sets `cantCover` and its thirst drops
  to 0 for the rest of the turn.
- **`DefenseManager`** — clusters adjacent enemy units into `DmGroup`s, scores their `danger`,
  and answers the most dangerous group by cutting it off or fighting it directly, pulling
  reinforcements closer via `MassMarchManager`. Thirst is `1.5 + 5 * danger` when
  `danger > 0.45`; a defense thirst ≥ 5 deactivates all spendings
  (`checkForGreatDanger`) so money goes to the emergency.

`AiMaster.perform()` iterates owned provinces; `performForSingleProvince` resets `aiData`,
computes perimeter/importance/solid defense/vicinity, merges surplus peasants, then runs
`applyActionsAndSpendings()`: up to 7 rounds of "apply the best `PossibleSpending`, apply the
best `MasterAction`", each chosen by highest `thirst` (must be ≥ 1). Afterwards it grabs
free captures (`checkForCasualGrab`), pulls idle units to the perimeter, buys towers to
protect the supply path of strength ≥ 2 armies (`checkToSupplyArmyWithTowers`, using
capital-path `dependentUnits` vulnerability), and improves local defense.

`AiMaster.buildUnit(hex, strength)` enforces the spawn rule centrally: when asked to build on
a hex outside `currentProvince` (capture-with-money, peaceful expansion), it redirects to
`findHexToStageUnit` — an own empty hex adjacent to the target, else any own empty hex — then
attacks the original target immediately when the staged unit is adjacent and the capture is
legal (`checkToAttackFromStagingHex`). It returns whether a unit was actually built (loop
guards in `AttackManager.tryToCaptureWithMoney` and `DefenseManager.tryToFightUnitWithMoney`
depend on that return value).

On PC, `AiMaster` for fraction 0 keeps a debug state string
(`updateLastState()` = camera + `EncodeManager.perform()`, exportable to clipboard) and can
print action/spending thirsts when `DebugFlags.closerLookMode` is set.

## Known limitations and blind spots

- **Strength range 1..4 is hardcoded everywhere**: `tryToBuildUnits` loops `for (i = 1; i <= 4)`,
  `PsType` stops at `unit4`, `AiMaster.buildUnit`/`mergeUnits` print "problem" above 4,
  `findHexAttractiveToBaron` special-cases `strength == 4`. Adding a unit tier means touching
  every AI class.
- **One-shot turns**: since `perform()` runs once and ends the turn, any heuristic that fails
  quietly (e.g. `AttackManager.cantCover`) simply means the AI does less that turn; there is no
  re-planning.
- **`Hex.getDefenseNumber()` is the only threat model** — max of building defense (town 1,
  tower 2, strong tower 3) and neighboring unit strengths. `getStrengthNecessaryToCapture`
  returns `defenseNumber + 1` (capped at 4; returns −1 for defense 4 under slay rules).
- **Ladder AIs are per-province greedy**; only `AiMaster` reasons about paths, cover, or enemy
  unit groups, and even it never coordinates across its own provinces in one plan.
- **No lookahead** — nobody simulates opposing moves; the Balancer's "attack the leader" sort
  is the only inter-player awareness in the ladder.
- **Diplomacy**: attackability is checked via `AiMaster.canFractionBeAttacked` (ENEMY relation
  only); the ladder relies on move-zone generation to enforce the same. `DiplomaticAI` (in
  `gameplay/diplomacy/`) handles treaty decisions separately from this package.
- **Provinces can still seal themselves in** — a unit can only be bought onto a hex with no
  building on it (`findHexToStageUnit`), so a province whose every hex carries a farm or tower can
  never field a unit again. `ArtificialIntelligence.buildingWouldSealProvince` keeps every AI from
  building itself into that corner, but conquest can still shrink a province down onto its own
  buildings, and nothing recovers from that.
- Dead code: `AiRestoredBalancerGeneric`/`AiRestoredBalancerSlay` (941/730 lines) are wired to
  nothing.

Related docs: [turn-cycle.md](turn-cycle.md), [economy.md](economy.md), [combat.md](combat.md).

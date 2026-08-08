# Units and buildings

Everything that can stand on a hex is either a `Unit`
(`core/src/yio/tro/antiyoy/gameplay/Unit.java`) or a solid object identified
by an integer constant from `Obj`
(`core/src/yio/tro/antiyoy/gameplay/Obj.java`). A hex stores at most one of
each: `Hex.unit` and `Hex.objectInside`.

## Obj constants

```java
public static final int PINE = 1;
public static final int PALM = 2;
public static final int TOWN = 3;
public static final int TOWER = 4;
public static final int GRAVE = 5;
public static final int FARM = 6;
public static final int STRONG_TOWER = 7;
```

These ids are serialized into save files, replays and level strings
(`Hex.encode()` writes `index1 index2 fraction objectInside`), so the list is
**append-only** — never renumber or reuse an id. `objectInside == 0` means
empty. Helper predicates on `Hex`: `containsTree()` (PINE or PALM),
`containsTower()` (TOWER or STRONG_TOWER), `containsBuilding()` (TOWN, TOWER,
FARM, STRONG_TOWER), `containsObject()` (`objectInside > 0`), `isFree()`.

## Units

`Unit` has no type field — only `int strength` (1–4). Strength is
simultaneously combat power, defense contribution, price multiplier and
upkeep tier.

| Strength | Price (`GameRules.PRICE_UNIT * strength`) | Upkeep/turn (generic) | Upkeep/turn (slay) |
|---|---|---|---|
| 1 | 10 | 2 (`TAX_UNIT_GENERIC_1`) | 2 |
| 2 | 20 | 6 (`TAX_UNIT_GENERIC_2`) | 6 |
| 3 | 30 | 18 (`TAX_UNIT_GENERIC_3`) | 18 |
| 4 | 40 | 36 (`TAX_UNIT_GENERIC_4`) | 54 |

Prices and generic taxes live in
`core/src/yio/tro/antiyoy/gameplay/rules/GameRules.java`; the per-tier tax
switch is `RulesetGeneric.getUnitTax` / `RulesetSlay.getUnitTax`.

Other `Unit` state: `readyToMove` (one action per turn, reset in
`GameController.prepareCertainUnitsToMove` at turn start), `currentHex` /
`lastHex` plus a `FactorYio` for movement animation, and jump-animation
fields. `Unit.getFraction()` is just `currentHex.fraction`.

Buying: `FieldManager.buildUnit(province, hex, strength)` checks
`province.canBuildUnit(strength)` (money >= price), deducts the price, and
either places the unit peacefully (merging if a friendly unit is there) or
captures the target hex (`buildUnitByAttack`, subject to the same attack rule
as movement). A freshly bought unit placed by attack is not ready to move;
one bought onto friendly ground is.

### Merging

`GameController.mergeUnits` combines two friendly units into one of strength
`unit1.strength + unit2.strength`, capped at 4 (`Ruleset.canMergeUnits`).
`FieldManager.isUnmergeableSituationDetected` blocks buying a unit onto a
friendly unit when the sum would exceed 4.

## Buildings

| Building | Obj id | Price | Upkeep/turn | Defense |
|---|---|---|---|---|
| Capital (`TOWN`) | 3 | — (auto-placed) | 0 | 1 |
| Tower | 4 | `PRICE_TOWER = 15` | `TAX_TOWER = 1` | 2 |
| Strong tower | 7 | `PRICE_STRONG_TOWER = 35` | `TAX_STRONG_TOWER = 6` | 3 |
| Farm | 6 | `PRICE_FARM = 12` + 2 per existing farm | 0 | 0 |

- **Capital**: every province with 2+ hexes has exactly one `Obj.TOWN`.
  Placement/relocation happens in `Province.placeCapitalInRandomPlace` and
  `Province.setCapital` when provinces split or lose their capital
  (`FieldManager.splitProvince`). When a capital's hex is captured, the
  building is destroyed like anything else; a surviving fragment gets a new
  capital. `FieldManager.destroyBuildingsOnHex` spawns a tree where a capital
  stood.
- **Towers** are built by `FieldManager.buildTower` / `buildStrongTower` on
  any owned hex; they only exist for their defense aura (see
  [combat.md](combat.md)) and cost upkeep.
- **Farms** can only be built adjacent to the capital or another friendly farm:
  `MoveZoneDetection.canBuildFarmOnHex` requires
  `hex.hasThisSupportiveObjectNearby(Obj.FARM)` or `...(Obj.TOWN)`.
  `FieldManager.buildFarm` charges `Province.getCurrentFarmPrice()`
  (see [economy.md](economy.md)). Each farm adds `FARM_INCOME + 1 = 5` to
  income under generic rules.
- Building on an occupied hex: `FieldManager.addSolidObject` calls
  `cleanOutHex` first, so building over a tree simply replaces it (no
  chop reward for buildings).

## Trees, palms, graves

- `PINE` spawns/spreads inland, `PALM` along the coast
  (`FieldManager.spawnTree` picks by `hex.isNearWater()`).
- Spreading runs once per round in `FieldManager.expandTrees` (before player
  0's turn). Generic rules (`RulesetGeneric`): a pine appears on a free hex
  with at least 2 adjacent trees, an adjacent pine ready to expand, and a 0.2
  roll; a palm on a free coastal hex with an adjacent palm ready to expand and
  a 0.3 roll. Slay rules are far more aggressive: pines at 0.8 and palms
  deterministically on every eligible coastal hex.
- A hex that just grew a tree is `blockToTreeFromExpanding` for one round.
- `GRAVE` is left by starvation deaths only
  (`FieldManager.killUnitByStarvation`) and turns into a tree at the owner's
  next turn start (`FieldManager.transformGraves`).
- Trees zero a hex's income; chopping one by moving/building a unit there
  pays `TREE_CUT_REWARD = 3` under generic rules.

## Movement

- `MoveZoneManager` (`core/src/yio/tro/antiyoy/gameplay/MoveZoneManager.java`)
  owns the highlighted zone and delegates computation to `MoveZoneDetection`.
- `MoveZoneDetection.detectMoveZone(startHex, strength, moveLimit)` is a BFS:
  friendly hexes propagate while decrementing `moveZoneNumber` from the move
  limit; a non-friendly hex is added iff
  `GameController.canUnitAttackHex(strength, fraction, adjHex)` and never
  propagates further (enemy hexes are always terminal).
- Selecting a unit uses `moveLimit = GameRules.UNIT_MOVE_LIMIT = 4`; building
  a new unit uses the no-limit overload (9001), so a bought unit can be placed
  anywhere in the province or on any capturable border hex.
- Within friendly territory a unit may stop on any hex without a building,
  merging if a mergeable friendly unit is there (`Unit.canMoveToFriendlyHex`).
- Mass march (`MassMarchManager`, triggered by hold via
  `FieldManager.marchUnitsToHex`) moves every ready unit of the selected
  province toward a target using the same 4-step move zones.

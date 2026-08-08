# Combat

Combat in this codebase is the original Antiyoy rank model. There is no HP,
no damage, no counter-attacks: a unit either can capture a hex or it cannot,
and anything on a captured hex dies instantly. The whole system is a handful
of small methods:

- `Hex.getDefenseNumber()` — positional defense of a hex
  (`core/src/yio/tro/antiyoy/gameplay/Hex.java`)
- `Ruleset.canUnitAttackHex(int unitStrength, Hex hex)` — the capture predicate,
  implemented by `RulesetGeneric` and `RulesetSlay`
  (`core/src/yio/tro/antiyoy/gameplay/rules/`)
- `GameController.moveUnitWithAttack(...)` — what actually happens on capture
  (`core/src/yio/tro/antiyoy/gameplay/GameController.java`)

## Strength tiers

`Unit.strength` is an `int` from 1 to 4 (peasant, spearman, baron, knight in
game terms). There are no other unit types. Strength doubles as both attack
power and defense value.

## Positional defense: `Hex.getDefenseNumber()`

A hex's defense number is the maximum protection provided by the hex itself
and its six same-fraction neighbours:

| Object | Defense value |
|---|---|
| `Obj.TOWN` (capital) | 1 |
| `Obj.TOWER` | 2 |
| `Obj.STRONG_TOWER` | 3 |
| Unit | its `strength` (1–4) |
| Farm, trees, grave, empty hex | 0 |

The method checks `this.objectInside` and `this.unit`, then loops over
`getAdjacentHex(i)` for `i` in 0..5, considering only neighbours where
`neighbour.active && neighbour.sameFraction(this)`, and takes the max of all
contributions. So a capital protects itself and its ring at level 1, a tower
at level 2, a strong tower at level 3, and every unit protects its own hex
and its friendly neighbours at its strength. Farms provide no defense.

The overload `getDefenseNumber(Unit ignoreUnit)` skips one specific unit —
used by the AI to evaluate defense as if a defender had moved away.

## Capture rule

`RulesetGeneric.canUnitAttackHex`:

```java
if (unitStrength == 4) return true;
return unitStrength > hex.getDefenseNumber();
```

- An attacker captures an enemy/neutral hex iff its strength is **strictly
  greater** than the hex's defense number.
- Under generic (Antiyoy) rules a strength-4 knight can attack **anything**,
  including hexes defended by another 4 or by a strong tower.
- Under Slay rules (`GameRules.slayRules`, `RulesetSlay.canUnitAttackHex`)
  there is no such exception: `unitStrength > hex.getDefenseNumber()` always,
  so a 4 cannot break another 4.

The predicate is routed through
`GameController.canUnitAttackHex(strength, fraction, hex)`, which delegates to
`DiplomacyManager.canUnitAttackHex` when `GameRules.diplomacyEnabled` (allies
cannot be attacked). `Hex.canBeAttackedBy(Unit)` is the per-hex wrapper.

## What happens on attack

`GameController.moveUnit(unit, target, province)` splits on
`isMovementPeaceful` (`unit.currentHex.sameFraction(target)`). For an attack,
`moveUnitWithAttack`:

1. `fieldManager.setHexFraction(destination, turn)` — the hex changes owner.
   This triggers `splitProvince` (defender's province may split; fragments of
   size 1 lose their buildings via `destroyBuildingsOnHex`, fragments without
   a capital get a new one placed), `checkToUniteProvinces` and
   `joinHexToAdjacentProvince` on the attacker's side.
2. `fieldManager.cleanOutHex(destination)` — whatever occupied the hex (unit,
   tower, farm, capital, tree) is destroyed outright. No grave is left by
   combat; graves come only from starvation (see below).
3. `unit.moveToHex(destination)` — sets `readyToMove = false`; the unit cannot
   act again this turn. There is no multi-attack.

Move range is limited to `GameRules.UNIT_MOVE_LIMIT = 4` steps through
friendly territory (see `MoveZoneDetection`); enemy hexes are only ever the
last step of a move.

## Merging

Two friendly units merge when a unit moves onto another
(`GameController.mergeUnits` via `moveUnitPeacefully`). The result strength is
`mergedUnitStrength = unit1.strength + unit2.strength` and the merge is
allowed only if the sum is `<= 4` (`Ruleset.canMergeUnits` in both rulesets,
and `GameController.canMergeUnits`). The merged unit is ready to move only if
both inputs were. Buying a new unit onto an existing friendly unit merges the
same way (`FieldManager.buildUnitPeacefully`); the freshly bought unit counts
as ready, so the merge result is movable iff the existing unit still was.
Units can be bought only on the buying province's own territory — capturing
enemy hexes with fresh recruits means buying near the border and moving the
unit, limiting the reach to its move zone.

## Trees, palms and graves (combat-adjacent)

- Trees (`Obj.PINE`, `Obj.PALM`) block a hex's income but have 0 defense. A
  unit moving onto a friendly tree hex chops it and the province earns
  `GameRules.TREE_CUT_REWARD = 3` (`RulesetGeneric.onUnitMoveToHex`; also on
  building a unit onto a tree, `onUnitAdd`). Slay rules pay no reward.
- Units that die of starvation (bankruptcy or being cut off from friendly
  hexes) become graves: `FieldManager.killUnitByStarvation` places
  `Obj.GRAVE`. At the owner's next turn start, `FieldManager.transformGraves`
  turns each grave into a tree (`spawnTree`: palm if `isNearWater()`, pine
  otherwise).
- Trees spread each round before player 0's turn (`FieldManager.expandTrees`;
  spawn conditions in `Ruleset.canSpawnPineOnHex` / `canSpawnPalmOnHex`).

See [economy.md](economy.md) for how upkeep causes starvation, and
[units-and-buildings.md](units-and-buildings.md) for costs and object ids.

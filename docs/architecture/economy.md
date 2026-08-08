# Economy

Money belongs to provinces, not players. `Province.money`
(`core/src/yio/tro/antiyoy/gameplay/Province.java`) starts at
`Province.DEFAULT_MONEY = 10` and every income/spend operation mutates that
one field. When a province splits, the largest surviving fragment inherits
the money (unless the captured hex was the capital) and the other fragments
start at 0 (`FieldManager.splitProvince`); when provinces unite, money sums
(`FieldManager.checkToUniteProvinces`).

All balance constants live in
`core/src/yio/tro/antiyoy/gameplay/rules/GameRules.java`; per-hex formulas
live in `RulesetGeneric` / `RulesetSlay`
(`core/src/yio/tro/antiyoy/gameplay/rules/`), selected by
`GameRules.slayRules`.

## The turn tick

At the start of each fraction's turn, `GameController.turnStartActions` runs
`collectTributesAndPayTaxes`, which adds `Province.getProfit()` to each of
that fraction's provinces:

```java
getProfit() = getIncome() - getTaxes() + getDotations()
```

`getDotations()` is diplomacy-only tribute
(`DiplomacyManager.getProvinceDotations`), zero unless
`GameRules.diplomacyEnabled`. (A first-turn correction,
`GameController.takeAwaySomeMoneyToAchieveBalance`, pre-deducts one tick of
profit from every fraction except player 0.)

## Income

`Province.getIncome()` sums `ruleset.getHexIncome(hex)` over the province.
Generic rules (`RulesetGeneric.getHexIncome`):

- hex with a tree: **0**
- hex with a farm: **`GameRules.FARM_INCOME + 1` = 5**
- any other hex: **1**

Slay rules: 1 per hex, 0 for trees, no farms.

Chopping a tree (unit moves onto or is built on a tree hex) pays
`TREE_CUT_REWARD = 3` under generic rules (`RulesetGeneric.onUnitMoveToHex`,
`onUnitAdd`).

## Taxes (upkeep)

`Province.getTaxes()` sums `ruleset.getHexTax(hex)`:

| Item | Generic | Slay |
|---|---|---|
| Unit strength 1 | `TAX_UNIT_GENERIC_1` = 2 | 2 |
| Unit strength 2 | `TAX_UNIT_GENERIC_2` = 6 | 6 |
| Unit strength 3 | `TAX_UNIT_GENERIC_3` = 18 | 18 |
| Unit strength 4 | `TAX_UNIT_GENERIC_4` = 36 | 54 |
| Tower | `TAX_TOWER` = 1 | 0 |
| Strong tower | `TAX_STRONG_TOWER` = 6 | 0 |
| Farm, capital | 0 | 0 |

## Prices

| Purchase | Cost | Where |
|---|---|---|
| Unit | `PRICE_UNIT * strength` = 10/20/30/40 | `RulesetGeneric.canBuildUnit`, `FieldManager.buildUnit` |
| Tower | `PRICE_TOWER` = 15 | `FieldManager.buildTower` |
| Strong tower | `PRICE_STRONG_TOWER` = 35 | `FieldManager.buildStrongTower` |
| Farm | `getCurrentFarmPrice()` (see below) | `FieldManager.buildFarm` |
| Tree (editor/debug) | `PRICE_TREE` = 10 | `FieldManager.buildTree` |

### Farm price scaling

`Province.getCurrentFarmPrice()`:

```java
return GameRules.PRICE_FARM + getExtraFarmCost();
```

`getExtraFarmCost()` adds **2 per farm already in the province**, so farms
cost 12, 14, 16, ... Affordability checks are `hasMoneyForFarm()`,
`hasMoneyForTower()`, `hasMoneyForStrongTower()`, `hasMoneyForTree()`.

## Bankruptcy and starvation

After income is collected, `GameController.checkForStarvation` (skipped in
replay mode) runs two checks for the current fraction:

- **Bankruptcy** (`checkForBankrupts`): if `province.money < 0` after the
  tick, money is reset to 0 and `FieldManager.killEveryoneByStarvation`
  replaces **every unit in the province** with a grave
  (`killUnitByStarvation` → `Obj.GRAVE`). Buildings survive.
- **Alone units** (`checkForAloneUnits`): a unit with
  `numberOfFriendlyHexesNearby() == 0` (its province was cut down to that one
  hex) also starves into a grave.

Graves become trees at the owner's next turn start
(`FieldManager.transformGraves`), which then zero the hex's income — the
classic death spiral.

## AI solvency

The AI checks affordability with `Province.canAiAffordUnit(strength,
turnsToSurvive)`: it requires
`money + turnsToSurvive * (getProfit() - getUnitTax(strength)) >= 0`, i.e.
the province must survive `strength + 1` turns (default) after buying.
`Province.getIncomeCoefficient()` (1/number of provinces of the fraction) is
used by AI heuristics, not by the economy itself.

See [units-and-buildings.md](units-and-buildings.md) for what the buildings
do and [combat.md](combat.md) for how money is ultimately spent.

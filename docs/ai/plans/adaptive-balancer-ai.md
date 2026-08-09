# Adaptive Balancer AI (no ships)

## Context

The highest normal difficulty ("balancer", `Difficulty.BALANCER = 4`) has two problems:

1. **Farm deadlock (confirmed in code).** This fork removed attack-by-purchase, so buying a unit requires a free own hex for staging (`findHexToStageUnit`, `ArtificialIntelligence.java:296`, needs `nothingBlocksWayForUnit()` = no unit AND no building). Meanwhile `tryToBuildFarms` (`ArtificialIntelligenceGeneric.java:26`) paves any free hex adjacent to town/farm while money lasts, and the balancer's `isOkToBuildNewFarm` (`AiBalancerGenericRules.java:72`) short-circuits all safety checks whenever `money > 2 * farmPrice` — which is almost always true for a rich province. Once every hex holds a building, the province can never build a unit again. Towers cause the same seal.
2. **No environmental adaptivity.** Spending order is fixed (towers → farms → units) regardless of whether the map is wide open, land-starved, or hostile.

Goal: fix the deadlock and make the balancer choose a per-province strategy each turn — EXPAND (lots of neutral land → flood strength-1 units), ECONOMY (limited land → farms), TURTLE (strong neighbors → defend, save money, then one big push). Ships stay untouched (the AI currently has zero ship code; keep it that way).

## Files

- `core/src/yio/tro/antiyoy/ai/ArtificialIntelligence.java` — seal-guard for towers, shared helper
- `core/src/yio/tro/antiyoy/ai/ArtificialIntelligenceGeneric.java` — seal-guard for farms, smarter farm placement
- `core/src/yio/tro/antiyoy/ai/AiBalancerGenericRules.java` — modes, adaptive spending
- `core/src/yio/tro/antiyoy/ai/AiBalancerSlayRules.java` — mirror (no farms in slay rules)
- New: `core/src/yio/tro/antiyoy/ai/ProvinceMode.java`, `core/src/yio/tro/antiyoy/ai/BalancerBrain.java`
- New: `desktop/src/yio/tro/antiyoy/desktop/AiSkirmishHarness.java` (test harness)

## Step 1 — Deadlock fix (shared, benefits all difficulties)

In `ArtificialIntelligence`:

```java
protected int countNonBuildingHexes(Province province)   // hexes with !containsBuilding()
protected boolean buildingWouldSealProvince(Province p)  // countNonBuildingHexes(p) <= MIN_FREE_HEXES + 1
// MIN_FREE_HEXES = 2
```

- Guard `tryToBuildTowers` (`ArtificialIntelligence.java:146`): skip build if `buildingWouldSealProvince`.
- Guard `tryToBuildFarms` loop (`ArtificialIntelligenceGeneric.java:26`): same check before each `buildFarm`.
- Fix the balancer bypass: in `AiBalancerGenericRules.isOkToBuildNewFarm` line 73, `money > 2 * price` may only return true if `!buildingWouldSealProvince(srcProvince)`.
- Replace random `findGoodHexForFarm` (`ArtificialIntelligenceGeneric.java:59`) with a scored pick over `province.hexList`: prefer interior hexes (`+numberOfFriendlyHexesNearby(hex)`), penalize perimeter hexes (`-5` if `hex.isInPerimeter()`) so frontier staging spots stay free.
- One-liner: initialize `propagationList = new ArrayList<>()` in the `AiBalancerGenericRules` constructor (declared at line 13, never initialized — latent NPE in `hasSafePathToTown`).

## Step 2 — Province mode assessment

New `ProvinceMode` enum: `EXPAND, ECONOMY, TURTLE`.

New helper `BalancerBrain` (package-private, instantiated by both balancer classes since they have no common balancer ancestor) with:

```java
ProvinceMode assessProvinceMode(Province province)
int countAdjacentNeutralHexes(Province province)     // active neutral land hexes adjacent to province (dedup via hex.flag)
int countNearbyEnemyStrength(Province province)      // per nearby province: armyStrength + 2*towers + 3*strongTowers
double fortifiedFrontierRatio(Province province)     // perimeter hexes facing enemy defenseNumber > 0 / all frontline hexes
```

Decision:

```java
if (neutral >= max(2, hexList.size() / 8))                      return EXPAND;
if (enemy > 3*own/2 || fortifiedFrontierRatio > 0.5)            return TURTLE;   // own = armyStrength + 2*ownTowers
return ECONOMY;
```

Computed once per province per turn, cached in a `HashMap<Province, ProvinceMode>` cleared at the top of `makeMove()` (Province objects are rebuilt between turns — never persist across turns).

## Step 3 — Mode-driven behavior in `AiBalancerGenericRules`

**Towers only when in danger.** Currently `needTowerOnHex` (`AiBalancerGenericRules.java:391`) builds at any frontline hex with defense gain ≥ 3 — even when the neighbor is harmless. Add a threat gate in the balancer's `needTowerOnHex`: a tower is justified only if the hex is actually at risk, i.e. some nearby enemy province could plausibly take it:

```java
boolean isHexInDanger(Hex hex) {
    updateNearbyProvinces(hex);
    for (Province enemy : nearbyProvinces) {
        // enemy can field a unit stronger than this hex's current defense
        int maxAffordable = strongestUnitAffordableBy(enemy);   // from enemy.money / PRICE_UNIT, capped 4
        int maxFielded = strongestUnitIn(enemy);
        if (Math.max(maxAffordable, maxFielded) > hex.getDefenseNumber()) return true;
    }
    return false;
}
```

`needTowerOnHex` = existing checks AND `isHexInDanger(hex)`. Provinces bordering only neutral land or weak enemies build zero towers and put the money into farms/units instead. Mode interaction: in EXPAND, towers are last in spending order anyway; in TURTLE, danger is by definition present, so towers build as before.

Override `spendMoney(Province)`:

- **EXPAND**: units → farms → towers. In `tryToBuildUnits`, first flood strength-1: `while (canProvinceBuildUnit(province,1) && province.canAiAffordUnit(1, 3) && tryToAttackWithStrength(province, 1))` — relaxed 3-turn survival horizon since each captured neutral hex adds +1 income. In `getAttackAllure` (line 216) add `+4` for neutral hexes in EXPAND mode (`+1` otherwise) so expansion targets free land first.
- **ECONOMY**: current order (towers → farms → units), strict `canAiAffordUnit(i, 5)`, no strength-1 flood. Farm guards from Step 1 apply.
- **TURTLE**: towers → farms → **save**. Suppress `tryToAttackWithStrength`; only `tryToReinforceUnits` (make it protected) for defense. Skip `checkToKillRedundantUnits` for TURTLE provinces (don't cull the army being saved). Push trigger:
  ```java
  boolean isPushTriggered(p) { return p.money >= min(200, max(90, 10 * countNearbyEnemyStrength(p))); }
  ```
  (90 ≈ three strength-3 units; 200 cap prevents infinite hoarding.) When triggered, run `tryToBuildUnits` iterating strength 3→2→1 so the wave is coordinated — units built via `tryToAttackWithStrength` attack immediately through `checkToAttackFromStagingHex`.

Mode visibility: field `currentMode` set at the top of `spendMoney` (provinces processed sequentially); `getAttackAllure` calls during `moveUnits()` read the per-turn HashMap lazily.

## Step 4 — `AiBalancerSlayRules` mirror

Slay rules have no farms; same `BalancerBrain`, same modes, same `isHexInDanger` tower gate. Override `spendMoney` (inherited base is towers → units): EXPAND = strength-1 flood first; ECONOMY = towers then units with strict affordability (economy in slay = don't overbuild units); TURTLE = towers + save + push, identical trigger. Tower seal-guard comes free from Step 1 (it lives in `ArtificialIntelligence.tryToBuildTowers`).

## Step 5 — No ships

Nothing active needed: balancer paths only use `detectMoveZone`/`buildUnit`/`buildTower`/`buildFarm`. Do not add any port/ship calls. Neutral-hex counting uses land adjacency only (`hex.active && isNeutral()`), which naturally excludes water.

## Verification

1. `make build` compiles.
2. New `AiSkirmishHarness` (modeled on existing `desktop/src/.../VerifyHarness.java`, run via the existing `-PmainClass` gradle hook — verify hook in `build.gradle` during implementation): all-balancer skirmish, 0 humans, auto end-turn for 300 turns. Checks:
   - **Deadlock regression**: fail if any AI province with `money >= PRICE_UNIT` has zero `nothingBlocksWayForUnit()` hexes for >5 consecutive turns.
   - **Strength baseline**: map one fraction to `AiExpertGenericRules` in the harness; new balancer should win the majority of ~20 seeded runs.
3. Manual spot-checks via `make run`: large open map (EXPAND flood visible turns 1–10), small crowded map (TURTLE saving then push).
4. Use harness runs to tune the three thresholds: neutral divisor `/8`, enemy ratio `3/2`, push threshold `90–200`.

## Implementation order

1. Deadlock guards + farm placement scoring + `propagationList` init (independently shippable).
2. `ProvinceMode` + `BalancerBrain`.
3. Mode-driven `spendMoney` / `tryToBuildUnits` / `getAttackAllure` / `checkToKillRedundantUnits` in generic balancer.
4. Slay-rules mirror.
5. Skirmish harness + threshold tuning.

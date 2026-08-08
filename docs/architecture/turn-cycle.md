# Turn cycle

All turn logic lives in `core/src/yio/tro/antiyoy/gameplay/GameController.java`.

## Whose turn

- `GameController.turn` is the index of the fraction whose turn it is. Fraction 0 is the
  first (usually human) player.
- `isPlayerTurn(int turn)` is simply `turn < playersNumber` — human seats are the first
  `playersNumber` fractions; everything after that is an AI from `aiList`.
- `isCurrentTurn(int fraction)` is `turn == fraction`.
- `getNextTurnIndex(int)` increments, skips `GameRules.NEUTRAL_FRACTION`, and wraps to 0 at
  `GameRules.fractionsQuantity`.

## The frame loop

`GameController.move()` runs every frame. The turn-relevant calls are:

- `checkForAiToMove()` — if it is not a player turn (and not `GameRules.replayMode`, where
  `replayManager.performStep()` runs instead), `performAiMove()` calls
  `aiList.get(turn).perform()` and then `applyReadyToEndTurn()`. An AI plays its whole turn
  inside one frame.
- `checkToEndTurn()` — if `readyToEndTurn` is set and `canEndTurn()` passes (camera settled,
  speed not paused, and — per `doesCurrentTurnEndDependOnAnimHexes()` — hex animations in
  `fieldManager.animHexes` finished), it runs the actual turn switch:

```java
readyToEndTurn = false;
endTurnActions();
turn = getNextTurnIndex();
turnStartActions();
```

A human ends their turn via `onEndTurnButtonPressed()` (end-turn button, or automatically from
`checkToSkipTurn()` when the player has no provinces left). In single-player it just calls
`applyReadyToEndTurn()`; in multiplayer (`endTurnInMultiplayerMode()`) with
`SettingsManager.cautiosEndTurnEnabled` it first shows `Scenes.sceneTurnStartDialog`.
`haveToAskToEndTurn()` optionally warns when units are still ready to move.

## Turn end — `endTurnActions()`

In order:

1. `checkToEndGame()` — delegates to `FinishGameManager.perform()` (see below).
2. `ruleset.onTurnEnd()` — empty in both `RulesetGeneric` and `RulesetSlay`
   (`gameplay/rules/`); it is a hook, not behavior.
3. `replayManager.onTurnEnded()` — records the end-turn action into the replay.
4. `fieldManager.diplomacyManager.onTurnEnded()`.
5. Every unit in `unitList` gets `setReadyToMove(false)` and `stopJumping()`.
6. `checkToApplyDebugPause()` (only with `DebugFlags.pauseAfterEachTurn`).

There is no HP, regeneration or tower-volley system in this codebase — combat is the classic
Antiyoy strength comparison resolved instantly in `GameController.moveUnitWithAttack()`.

## Turn start — `turnStartActions()`

In order:

1. `selectionManager.deselectAll()`.
2. If `isCurrentTurn(0)` (once per full round): `matchStatistics.onTurnMade()`, global
   statistics update (single-player), `fieldManager.expandTrees()`, and the debug-pause flag.
   `expandTrees()` (`FieldManager.java`) collects hexes where
   `ruleset.canSpawnPalmOnHex` / `canSpawnPineOnHex` pass, spawns the new trees, then clears
   the one-round `blockToTreeFromExpanding` flag.
3. `prepareCertainUnitsToMove()` — every unit for which `isUnitValidForMovement(unit)` holds
   (`isCurrentTurn(unit.getFraction())` and at least one friendly hex nearby) gets
   `setReadyToMove(true)` and starts jumping.
4. `fieldManager.transformGraves()` — every `Obj.GRAVE` on a hex of the current fraction turns
   into a tree via `spawnTree(hex)` (palm on coast, pine inland) and is blocked from expanding
   this round. The source comment mandates the ordering: after `expandTrees`, before the
   bankrupt check.
5. `collectTributesAndPayTaxes()` — each province of the current fraction gets
   `province.money += province.getProfit()`, where `Province.getProfit()` =
   `getIncome() - getTaxes() + getDotations()`.
6. `checkForStarvation()` (skipped in `GameRules.replayMode`):
   - `checkForBankrupts()` — any current-fraction province with `money < 0` is reset to 0 and
     `fieldManager.killEveryoneByStarvation(province)` replaces its units with graves;
   - `checkForAloneUnits()` — current-fraction units with
     `numberOfFriendlyHexesNearby() == 0` die via `fieldManager.killUnitByStarvation`.
7. `checkToUpdateCacheTextures()` — refreshes the field texture cache once per round.
8. Player turn: `resetCurrentTouchCount()`, `snapshotManager.onTurnStart()` (recycles all undo
   snapshots — see below), `jumperUnit.startJumping()`, `checkToSkipTurn()`,
   `fieldManager.fogOfWarManager.updateFog()`. AI turn: hex anim factors are snapped to done.
9. `fieldManager.diplomacyManager.onTurnStarted()`.
10. `fieldManager.checkToFocusCameraOnCurrentPlayer()`.
11. `checkToAutoSave()` — autosaves when `SettingsManager.autosave` is on and `turn == 0`.

Consequences worth remembering:

- Income lands at the *start* of a fraction's turn and starvation is checked immediately after,
  in the same step list.
- A freshly bought unit cannot move (`FieldManager` builds it with `readyToMove == false`);
  it becomes movable in step 3 of its owner's next turn start.
- `takeAwaySomeMoneyToAchieveBalance()` compensates at level start for the fact that every
  fraction except fraction 0 receives income before its first chance to act.

## Snapshots and undo

`gameplay/SnapshotManager.java` and `gameplay/LevelSnapshot.java`.

- `GameController.takeSnapshot()` → `SnapshotManager.takeSnapshot()`, which is a no-op unless
  `isPlayerTurn()`. It is called *before* every mutating player action: unit builds/moves and
  building construction in `FieldManager` (e.g. lines around `buildUnit`/`buildTower`) and tip
  confirmation in `SelectionManager`.
- `LevelSnapshot.take()` deep-copies the field matrix, provinces, active hexes, selection hex,
  match statistics, the replay buffer, diplomacy info and namings; `recreate()` restores them.
- `SnapshotManager` pools snapshots (`MAX_SNAPSHOTS` = 25 live, `FREE_SNAPSHOTS_LIMIT` = 30
  pooled). `onTurnStart()` — called from `turnStartActions()` step 8 — moves all live snapshots
  back to the free pool, so **undo only reaches back to the start of the current player turn**.
- Undo button → `menu/behaviors/gameplay/RbUndo` → `SnapshotManager.undoAction()`, which
  recreates the newest snapshot and discards it.
- `GameController.onInitialSnapshotRecreated()` resets `turn = 0` when a match restarts from
  its initial snapshot.

## Match end

`checkToEndGame()` runs at the start of `endTurnActions()` and delegates to
`gameplay/FinishGameManager.perform()` (skipped in replay mode). It switches on
`FinishGameManager.goalType` (`gameplay/GoalType.java`): `def` (last fraction standing),
`destroy_everyone`, `diplomatic_victory`, `destroy_target_kingdom`, `ensure_target_victory`,
`survive_long_enough` (`arg1` turns), `reach_target_income` (`arg1` income). If no human
province survives (`areDefaultConditionsForced()`), the default win-by-elimination check is
forced regardless of the level's goal. `GameController.forceGameEnd()` (surrender/skip path)
declares the fraction with the most hexes the winner by collapsing the province list and
re-running the check. Ending the game leads to `Scenes.sceneAfterGameMenu`.

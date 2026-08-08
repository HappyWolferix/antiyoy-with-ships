# Persistence

Everything is hand-rolled string formats stored in libGDX `Preferences` (there is no
`GdxDataManager` class in this repo — each subsystem calls `Gdx.app.getPreferences(name)`
directly). None of the formats carry a version marker, so **`Obj` ids and every encoding are
append-only**: `gameplay/Obj.java` ints (`PINE=1 … STRONG_TOWER=7`) are written raw into level
codes, legacy hex strings, save slots, and replay actions. Renumbering or reordering tokens
silently corrupts every existing save, shared level, and replay. Never introduce Java
serialization.

Three text formats plus an in-memory snapshot:

| Format | Producer / consumer | Used for |
| --- | --- | --- |
| Modern level code | `gameplay/data_storage/EncodeManager` / `DecodeManager` | Editor export/import, user levels, restart |
| Legacy hex string | `LegacyExportManager` / `LegacyImportManager` + `GameSaver` | Save slots, replay initial state, legacy user levels |
| Preferences save slots | `GameSaver` via `menu/save_slot_selector/SaveSystem` | Saved games |
| `LevelSnapshot` | `gameplay/LevelSnapshot` (via `SnapshotManager`) | Undo (in memory, not text) |

Two small interfaces mark participants: `gameplay/data_storage/EncodeableYio`
(`encode()`/`decode(String)`, modern format) and `gameplay/SavableYio`
(`saveToString()`/`loadFromString`, preferences blobs like `NamingManager`).

## 1. Modern level code — `EncodeManager` / `DecodeManager`

`EncodeManager.perform()` builds a single string of `#section:`-delimited parts, starting with
the watermark `antiyoy_level_code`. Section order: `level_size`, `general`
(`difficulty playersNumber fractionsQuantity`), `map_name`, `editor_info`
(`editorChosenColor editorDiplomacy editorFog diplomaticRelationsLocked`), `land`, `units`,
`provinces`, `relations`, `coalitions`, `messages`, `goal`, `real_money`
(`index1 index2 money` per province capital), terminated by `#`.
`performToClipboard()` also copies it to the system clipboard.

- `land` = comma-joined `Hex.encode()` = **`index1 index2 fraction objectInside`** (4 tokens);
  `Hex.decode()` re-adds the solid object when `objectInside > 0`.
- `units` = comma-joined `Unit.encode()` = **`index1 index2 strength readyToMove`** (4 tokens).

Compatibility mechanics: missing sections early-return (`DecodeManager.getSection(name)`
returns `null`), and optional trailing tokens are read behind `split.length` guards (see
`applyEditorInfo`, which guards `diplomaticRelationsLocked` with `split.length > 3` — the
pattern to copy when appending a token). Validation is `DecodeManager.isValidLevelCode()`
(watermark + `level_size` + `units` + `land` present). `DecodeManager` also offers cheap
`extract*` peeks (`extractMapName`, `extractDifficulty`, `extractLevelSize`, …) that read one
section without loading.

`ImportManager.launchGameFromClipboard()` routes clipboard text: strings containing the
watermark go through `DecodeManager` (`LoadingType.editor_import`); strings with `/` and `#`
but no watermark are legacy and go to `LegacyImportManager.importLevel()`.

## 2. Legacy hex string — `LegacyExportManager` / `LegacyImportManager`

`LegacyExportManager.getFullLevelString()` =
`difficulty levelSize playersNumber fractionsQuantity` + `/` + `getActiveHexesString()`, which
is `#`-separated per-hex records of exactly **seven space-separated ints**:

```
index1  index2  fraction  objectInside  unitStrength  unitReadyToMove  provinceMoney
```

Only `fieldManager.activeHexes` are written; province money is duplicated onto every hex of
the province (10 for hexes with no province). `LegacyImportManager.applyFullLevel()` parses
the basic-info prefix into `LoadingParameters`; the hex part is decoded later by
`GameSaver.activateHexByString()`, which parks money in `hex.moveZoneNumber` until
`recreateMap()` runs `detectProvinces()` and copies it to `province.money`.
`getHexSnapshotByString` allocates `int[7]` and ignores extra tokens, so absent trailing
tokens default to 0 — append-only extension with 0 meaning "absent" is the compatible path.

## 3. Save slots — `GameSaver` + `SaveSystem`

`GameSaver.saveGame(prefsName)` writes one `Preferences` file per slot:

- scalar keys from `saveBasicInfo()`: `save_turn`, `save_color_number`, `save_level_size`,
  `save_player_number`, `save_campaign_mode`, `save_current_level`, `save_difficulty`,
  `save_color_offset`, `slay_rules`, `date`, `fog_of_war`, `diplomacy`, `user_level_mode`,
  `ul_key`, `editor_color_fix_applied`, `lock_relations`;
- match statistics (`save_stat_*`), diplomacy (`diplomacy_info` via
  `DiplomacyInfoCondensed`), debts, `goal` (`FinishGameManager.encode()`), `namings`,
  `initial_level` (for replays/restart);
- `save_active_hexes` — the legacy 7-int hex string from `LegacyExportManager`.

`loadGame(prefsName)` reads `save_active_hexes`, funnels everything through
`LoadingParameters` → `LoadingManager.startGame()` (`LoadingType.load_game`), then restores
namings, statistics, diplomacy, goal, and debts. `GameSaver.detectRules()` sniffs
slay-vs-generic from field contents: any neutral hex, `Obj.FARM`, or `Obj.STRONG_TOWER`
forces generic rules.

`menu/save_slot_selector/SaveSystem` manages the slot list in prefs file
`antiyoy.slot_prefs` (slot keys, names, descriptions; `AUTOSAVE_KEY = "autosave"`), and does a
one-time import of the pre-slot-UI files `save`, `save_slot0..4`.

## 4. Undo snapshots — `LevelSnapshot`

In-memory only. `take()` copies the whole `field[][]` via `Hex.getSnapshotCopy()` (`active`,
`fraction`, `objectInside`, `selected`, plus `Unit.getSnapshotCopy()`), provinces, diplomacy
(as a `DiplomacyInfoCondensed` string), match statistics, the replay buffer, and selection.
`recreate()` rebuilds the field, re-runs `detectProvinces()`, and restores the rest.
`SnapshotManager` keeps the history.

## Replays — `gameplay/replays/`

A replay is the **legacy full level string** (initial state,
`Replay.updateInitialLevelString()`, recorded only when `SettingsManager.replaysEnabled`)
plus an action log. Actions buffer per turn and commit on `RepAction.TURN_ENDED`
(`Replay.addAction`). Serialization (`Replay.convertActionsToString`) is
`type-saveInfo()` records joined by `#`, stored in prefs keys `initial` and `actions`.

`RepAction` type ids (append-only, `RepActionFactory` maps them back):

| id | Action | id | Action |
| --- | --- | --- | --- |
| 0 | `RaUnitBuilt` | 6 | `RaTurnEnded` |
| 1 | `RaUnitMoved` | 7 | `RaCitySpawned` |
| 2 | `RaTowerBuilt` (`index1 index2 strongBoolean`) | 8 | `RaUnitDiedFromStarvation` |
| 3 | `RaFarmBuilt` | 9 | `RaHexFractionChanged` |
| 4 | `RaPalmSpawned` | 10 | `RaUnitSpawned` |
| 5 | `RaPineSpawned` | | |

Playback: `GameRules.replayMode` makes `GameController.checkForAiToMove()` call
`ReplayManager.performStep()` instead of the AI; `Replay.performStep()` executes actions until
the next `TURN_ENDED`. Combat outcomes are not recorded — they replay deterministically from
the initial state plus these actions. `ReplaySaveSystem` (singleton, prefs file
`antiyoy.replays`) manages slot keys and renames.

Known gap: replays recorded before the spawn rule change (units can no longer be bought onto
enemy hexes) may contain `RaUnitBuilt` actions targeting enemy territory; `buildUnit` now
rejects those, so such actions log "Problem in RaUnitBuilt.perform()" and are skipped —
old replays of that kind desync. Accepted limitation.

## Campaign and user levels

- **Campaign**: `gameplay/campaign/CampaignLevelFactory.createCampaignLevel(index)` first
  checks `LevelPackOne`/`LevelPack2..24` (hardcoded legacy level strings launched via
  `LoadingType.campaign_custom_legacy` / `campaign_custom`), else generates a level with a
  deterministic seed (`LoadingType.campaign_random`). Progress is a space-separated index
  list in key `completed_levels` of prefs file `antiyoy.progress`
  (`CampaignProgressManager`; `antiyoy.progress.slay` is declared but unused), with one-time
  import of the old `main`/`progress` integer.
- **User levels**: `gameplay/user_levels/` holds ~400 built-in maps. `AbstractLegacyUserLevel`
  subclasses return a legacy `getFullLevelString()`; `AbstractUserLevel` subclasses return a
  modern `getLevelCode()` (and derive fog/diplomacy via `DecodeManager.extract*`). Launched
  via `LoadingType.user_level_legacy` / `user_level`; `UserLevelsManager` tracks completion in
  prefs.
- **Editor slots**: `gameplay/editor/EditorSaveSystem` (prefs file `editor`, keys
  `slotN`/`slotN:name`) stores modern level codes per slot; `EncodeManager.encodeMapName()`
  reads the slot name back from these prefs when exporting.

## Other preferences

Scattered small stores, all plain `Gdx.app.getPreferences`: `settings`
(`SettingsManager`), `campaign_options` (slay toggle read by `CampaignLevelFactory`),
`OneTimeInfo`, `menu/SingleMessages`, `GlobalStatistics`, `SkipLevelManager`,
`CustomCityNamesManager`, and skirmish/UI scene state.

## Save/load flow summary

Save: `SaveSystem` slot UI → `GameSaver.saveGame` → prefs keys + legacy hex string.
Load: any entry point (slot, clipboard import, campaign, user level, replay) fills
`LoadingParameters` and calls `LoadingManager.startGame()`, which branches on
`LoadingType` to recreate the field either from `activeHexes` (legacy path,
`GameSaver.beginRecreation()`) or from a modern `levelCode` (`DecodeManager.perform()`).

## Extending a format

1. Prefer deriving the value from existing data; second best, keep it turn-scoped and add it
   only to `LevelSnapshot`/`getSnapshotCopy()`.
2. Otherwise append: a guarded trailing token in `Hex.encode()`/`Unit.encode()` (modern), a
   trailing int in `LegacyExportManager.getHexString()` plus a widened array in
   `GameSaver.getHexSnapshotByString()` (legacy, 0 = absent), and the snapshot copy.
3. Never renumber `Obj` ids or `RepAction` type ids, and never reorder existing tokens.

Related docs: [overview.md](overview.md), [turn-cycle.md](turn-cycle.md).

# UI

Antiyoy's UI is entirely hand-rolled on top of libGDX's `SpriteBatch` — no Scene2D, no skins,
no stage. Everything lives under `core/src/yio/tro/antiyoy/menu/`. Screen-space UI (menus and
the in-game HUD) is drawn by the **menu layer**; world-space visuals (hexes, units, move zone,
selection tip) are `GameRender` subclasses in
`core/src/yio/tro/antiyoy/gameplay/game_view/`.

## The pieces

- **`MenuControllerYio`** (`menu/MenuControllerYio.java`) — owns two flat lists:
  `ArrayList<ButtonYio> buttons` and `ArrayList<InterfaceElement> interfaceElements`. Its
  `move()` ticks every element each frame; `touchDown`/`touchDragged`/`touchUp` walk the lists
  back-to-front and stop at the first consumer. There is no scene graph — "which scene is
  showing" is just "which buttons/elements are currently visible".
- **`ButtonYio`** (`menu/ButtonYio.java`) — the workhorse widget: a `RectangleYio`
  position, a texture (either loaded via `MenuControllerYio.loadButtonOnce(button, "file.png")`
  or text rendered onto a texture by `ButtonRenderer`), a `Reaction`, and three `FactorYio`s
  (`appearFactor`, `selectionFactor`, `selAlphaFactor`). Buttons are created through
  `ButtonFactory.getButton(position, id, text)`; integer ids make them reusable across scene
  invocations via `MenuControllerYio.getButtonById(id)`.
- **`InterfaceElement`** (`menu/InterfaceElement.java`) — abstract base for everything that is
  not a plain button (panels, dialogs, lists, keyboards). Contract: `move()`, `getFactor()`,
  `appear()`/`destroy()`, `isVisible()`, `touchDown/Drag/Up`, `setPosition(RectangleYio)` and
  `getRenderSystem()`, which returns the `MenuRender` that knows how to draw it.
- **`Reaction`** (`menu/behaviors/Reaction.java`) — button click handlers. Common ones are
  static singletons on `Reaction` (`rbEndTurn`, `rbUndo`, `rbBuildUnit`, `rbBuildSolidObject`,
  `rbPauseMenu`, ...) implemented as classes in `menu/behaviors/` and its subpackages
  (`gameplay/`, `editor/`, `menu_creation/`, `help/`).

## Scenes

`menu/scenes/Scenes.java` holds one public static field per scene
(`Scenes.sceneMainMenu`, `Scenes.sceneGameOverlay`, `Scenes.scenePauseMenu`, ...), all
subclasses of `menu/scenes/AbstractScene.java`. A scene is not a screen object — it is a
factory: `create()` (the single abstract method) spawns/reuses its buttons and elements through
`buttonFactory` and `menuControllerYio`, typically bracketed by
`menuControllerYio.beginMenuCreation()` / `endMenuCreation()`, which destroy the previous
scene's widgets and order spawning/dying buttons for correct draw order. Navigation is simply
calling another scene's `create()`. In-game scenes live in `menu/scenes/gameplay/` (many extend
`AbstractModalScene`); editor scenes in `menu/scenes/editor/`.

## Rendering

Two layers, composed in `YioGdxGame.render()` (`core/src/yio/tro/antiyoy/YioGdxGame.java`):

- **Game layer** — `GameView.render()` (`gameplay/game_view/GameView.java`) draws the world
  through the `GameRender` subclasses registered in `GameRendersList` (`RenderBackgroundCache`,
  `RenderSolidObjects`, `RenderUnits`, `RenderMoveZone`, `RenderSelectedHexes`, `RenderTip`,
  `RenderFogOfWar`, ...). The static field is baked into cache textures; animated hexes and
  units are drawn live on top.
- **Menu layer** — `MenuViewYio.render(renderAliveButtons, renderDyingButtons)`
  (`menu/MenuViewYio.java`) draws buttons (their pre-rendered textures) and, for each
  `InterfaceElement`, calls `element.getRenderSystem()`. All `MenuRender` subclasses live in
  `menu/render/` and are registered as public static fields on `menu/render/MenuRender.java`
  (e.g. `MenuRender.renderFastConstructionPanel`); the base-class constructor adds each
  instance to a list that `MenuRender.updateRenderSystems(menuViewYio)` refreshes.

## FactorYio animations

`core/src/yio/tro/antiyoy/factor_yio/FactorYio.java` is the universal animation primitive: a
scalar `f` in [0, 1] advanced each frame by a pluggable `MoveBehavior`
(`moveBehaviorSimple`, `Lighty`, `Material`, `Approach`, `Playful` — indices 0–4 passed to
`appear(moveMode, speed)` / `destroy(moveMode, speed)`). UI code reads `factor.get()` every
frame to interpolate position and alpha: a button's on-screen rectangle is its target
`position` blended with an off-screen delta chosen by its `Animation` enum
(`menu/Animation.java`: `up`, `down`, `left`, `right`, `center`, ...). Nothing is event-driven;
`move()` + `get()` is the whole animation system. The same class animates gameplay visuals
(selection blackout, hex appear animations, `SelectionManager.tipFactor`).

## In-game HUD

Created by `Scenes.sceneGameOverlay` (`menu/scenes/gameplay/SceneGameOverlay.java`) when a game
starts (`gameplay/loading/LoadingManager` and `RbResumeGame` call `create()`; it redirects to
`sceneEditorOverlay` / `sceneAiOnlyOverlay` in those modes):

- menu button (id 30, `menu_icon.png`, top-right) → `Reaction.rbPauseMenu`;
- undo button (id 32, `undo.png`, bottom-left) → `Reaction.rbUndo`;
- **end turn button** — bottom-right, not a `ButtonYio` but a dedicated
  `menu/EndTurnButtonElement.java` (rendered by `menu/RenderEndTurnButtonElement.java`), with a
  long-tap factor `ltFactor` and an `onSpaceButtonPressed()` keyboard hook; it ends up calling
  `GameController.onEndTurnButtonPressed()`. Tutorial scripts toggle it via
  `Scenes.sceneGameOverlay.endTurnButtonElement`.

### Selection / tip panel

When the player selects a province, `FieldManager.showBuildOverlay()` creates either:

- `Scenes.sceneSelectionOverlay` (`menu/scenes/gameplay/SceneSelectionOverlay.java`) — the
  classic bottom panel: build-unit button (id 39, `Reaction.rbBuildUnit`), build-tower button
  (id 38, `Reaction.rbBuildSolidObject`), plus diplomacy (id 36) and diplomatic-log (id 33)
  buttons on the left when `GameRules.diplomacyEnabled`; or
- `Scenes.sceneFastConstructionPanel` when `SettingsManager.fastConstructionEnabled` — the
  `menu/fast_construction/FastConstructionPanel.java` element (drawn by
  `menu/render/RenderFastConstructionPanel.java`) with one-tap items per `FcpActionType`:
  units 1–4, farm, tower, strong tower, undo, end turn, diplomacy, log;

plus `Scenes.sceneFinances` (money readout, `menu/income_view/MoneyViewElement`).

Pressing the build buttons sets `SelectionManager.selectionTipType`
(`gameplay/SelectionTipType.java`: `TOWER` 0, `UNIT_1..UNIT_4` 1–4, `FARM` 5, `STRONG_TOWER` 6,
`TREE` 7) and raises `SelectionManager.tipFactor`; the floating "what you are about to build"
tip is drawn in the *game* layer by `gameplay/game_view/RenderTip.java`. Tapping a hex then
routes through `SelectionManager.buildSomethingOnHex(focusedHex)`.

## Adding a new button or scene

1. **Button in an existing scene**: in the scene's `create()`, call
   `buttonFactory.getButton(generateSquare(x, y, size), someUnusedId, textOrNull)`, then
   `setReaction(...)` (a new class in `menu/behaviors/` or an anonymous `Reaction`),
   `setAnimation(Animation.xxx)`, and either `menuControllerYio.loadButtonOnce(button, "icon.png")`
   for an icon or let `ButtonRenderer` render its text. Pick an id not used by any live scene —
   ids are the identity for reuse and `destroyButton(id)`.
2. **New scene**: subclass `AbstractScene`, implement `create()` between
   `beginMenuCreation()`/`endMenuCreation()`, add a static field plus construction in
   `menu/scenes/Scenes.java` (mirroring the existing entries), and open it with
   `Scenes.sceneMyThing.create()`.
3. **New interactive HUD element**: copy the pattern of
   `menu/speed_panel/SpeedPanel.java` — an `InterfaceElement` with `position`/`viewPosition`
   rectangles and a `FactorYio appearFactor`, a `MenuRender` subclass in `menu/render/`
   registered as a static field on `MenuRender`, and `getRenderSystem()` returning it; add it
   with `menuControllerYio.addElementToScene(element)` from a scene.
4. **World-space visual**: a `GameRender` subclass registered in
   `gameplay/game_view/GameRendersList.java` instead.

See also: [turn-cycle.md](turn-cycle.md).

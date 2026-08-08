# menu/ — overlay

All UI: scenes, widgets, renderers. **Hand-rolled on libGDX primitives — no Scene2D
widgets.** Control flow goes through `MenuControllerYio`.

Read `docs/architecture/ui.md` first (scenes, buttons, in-game HUD, selection tips).

Local rules:

- New UI elements follow the existing pattern: an element class implementing
  `InterfaceElement`, a matching render class, registered with `MenuControllerYio` — copy
  the closest existing widget rather than inventing a new pattern.
- All animation via `FactorYio`; positions in normalized coordinates scaled by screen size,
  like neighbouring code.
- Sprite/atlas changes also need `make sprites` (regenerates atlases and `_low`/`_lowest`
  variants) — editing a png alone is not enough.
- In-game HUD state usually surfaces through `SelectionManager` / `SelectionTipType`;
  check `docs/architecture/ui.md` before adding a new channel.

Verify: `make run`, exercise the screen/widget at least at default and one non-default
graphics quality (sprites have low-res variants).

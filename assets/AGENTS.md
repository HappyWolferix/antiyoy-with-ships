# assets/ — overlay

Game assets: textures/atlases, sounds, music, fonts, language files.

Local rules:

- **Full-size pngs are the source; atlases and `_low`/`_lowest` variants are generated.**
  After editing any sprite png, run `make sprites` — never hand-edit an atlas or a
  generated low-res variant. Currently `assets/field_elements` is the regenerated
  directory (Makefile `SPRITE_DIRS`); add a directory there if its pngs start being edited.
- Sprite naming and atlas usage from the code side is documented in
  `docs/architecture/ui.md`.
- Translations live in `languages.xml` and share a key set — a new key must be added for
  every language (English value as placeholder is fine); loading is in
  `core/src/yio/tro/antiyoy/stuff/LanguagesManager.java`.
- Keep new asset file names lowercase_with_underscores, matching the existing convention.

Verify: `make sprites` (when sprites changed), then `make run` and look at the asset
in-game at more than one graphics quality.

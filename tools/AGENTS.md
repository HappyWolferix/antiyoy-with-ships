# tools/ — overlay

Offline build tools, compiled and run outside the game via `make`.

- `RebuildAtlas.java` — regenerates libGDX texture atlases and the `_low`/`_lowest` png
  variants from full-size pngs. Driven by `make sprites`; the atlas directories it
  processes are listed in the Makefile's `SPRITE_DIRS`.
- `validate_ai_docs.sh` — the docs feedback loop behind `make validate-ai-docs`: checks
  that every path in `docs/ai/manifest.yml` exists, every folder `AGENTS.md` is registered
  in the manifest, and relative Markdown links in `docs/` resolve.

Local rules:

- Tools may use modern Java (they are not part of the Java-6 game source), but must run
  headless (`-Djava.awt.headless=true` for anything AWT-based).
- A new tool gets a Makefile target and a row in `docs/ai/references/command-matrix.md`.

Verify: `make sprites` (or `make validate-ai-docs` for the validator itself).

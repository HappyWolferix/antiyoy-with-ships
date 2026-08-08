# Platform-Neutral AI Setup

This directory is the canonical, platform-neutral source for AI agent rules, routing, and
references in this repository. The root `AGENTS.md` is the entry point; everything it links
lives here or in `docs/architecture/`.

## Layout

- `manifest.yml` — the single routing manifest: keywords → docs, folders → AGENTS.md overlays,
  task routing, feedback loops, gotchas. **This is the file to match a task against.**
- `rules/` — stable project rules, always applied:
  - `code-style.md` — Java 6 level, GameRules constants, logging, naming.
  - `boundaries.md` — what never to change (Obj ids, save formats, libGDX pin).
  - `verification.md` — the feedback-loop ladder and when to run each loop.
  - `documentation.md` — keeping docs true, diagram authoring, manifest maintenance.
- `references/` — supporting maps:
  - `module-map.md` — folder-by-folder map with the overlay AGENTS.md of each.
  - `command-matrix.md` — every make/gradle command and what it verifies.
- `implementation-plans/` — AI-maintained plans. `todo/` is pending or in-review work;
  `done/` is historical record and may describe a state the code has moved past.
  Do **not** load plans automatically; load one only when the task references it.

## Module Overlays

The root `AGENTS.md` is canonical. Folder-local `AGENTS.md` files are overlays: load the root
first, then every overlay on the path to the files you are changing. Overlays add local rules
and pointers; they never override root rules. Overlays are indexed in `manifest.yml` under
`folders`.

## Maintenance Policy

When adding or changing agent guidance:

1. Update the canonical file under `docs/ai/` (or the folder overlay).
2. Update `manifest.yml` if a new file or keyword should be discoverable.
3. Run `make validate-ai-docs`.

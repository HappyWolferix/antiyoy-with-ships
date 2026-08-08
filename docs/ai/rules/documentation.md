# Documentation rules

## Keeping docs true

`docs/architecture/` describes how the game **currently works** — reference material, not a
changelog, with no supersede chains. Git history is the history.

- If you change behaviour, update the affected `docs/architecture/*.md` **in the same commit**.
- If you introduce a new concept, add its keywords to `docs/ai/manifest.yml`.
- If a doc and the code disagree, the code is right — fix the doc.
- Every number quoted in a doc lives in `gameplay/rules/GameRules.java` (or the `Ruleset`
  classes for income/upkeep formulas).
- "Known gaps" sections describe accepted limitations, not open bugs.
- After any docs change, run `make validate-ai-docs`.

## Adding a new architecture document

1. Create `docs/architecture/<topic>.md`. Structure: what it does now, key classes,
   key decisions (why), known gaps, and a closing list of manual verification checks.
2. Register it in `docs/ai/manifest.yml` under `docs:` with `summary`, `keywords`, `answers`.
3. Add a row to `docs/architecture/README.md`.

## Diagrams

Diagrams are authored as fenced ```plantuml or ```mermaid blocks embedded directly in the
Markdown document — the source lives with the text it illustrates. Guidelines:

- Prefer small, single-purpose diagrams: one component diagram, one sequence/activity per
  flow. A diagram that needs scrolling is two diagrams.
- Use plain syntax (no skins requiring external includes) so any renderer works.
- Rendering is optional and on demand; GitHub and most IDE plugins render the fences.
  Rendered images, if generated, go to `docs/architecture/diagrams/` and are never the
  source of truth.

## Implementation plans

Plans live in `docs/ai/implementation-plans/`, are AI-maintained, and are never loaded
automatically. Move a plan from `todo/` to `done/` when implemented; `done/` plans are
historical and not kept in sync with the code afterwards.

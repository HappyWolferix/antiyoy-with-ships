# Antiyoy documentation

Two subtrees:

- **[`architecture/`](architecture/README.md)** — how the game currently works and why:
  codebase map, key decisions (with diagrams), and one doc per feature area.
  This is the maintained spec; if a doc and the code disagree, the code is right — fix
  the doc.
- **[`ai/`](ai/README.md)** — the AI agent configuration: the routing manifest
  ([`ai/manifest.yml`](ai/manifest.yml)), always-applied rules, references, and
  implementation plans.

If you are an AI assistant: start with the root [`AGENTS.md`](../AGENTS.md), then match
your task against [`ai/manifest.yml`](ai/manifest.yml) to load only the two or three
docs you need.

Build and run instructions live in [`../RUNNING.md`](../RUNNING.md).

# Command matrix

All commands run from the repo root. `make` handles JDK discovery (falls back to
`~/.local/opt/jdk-21*` when `JAVA_HOME` is not a JDK) — prefer it over raw gradle.

| Command | What it does | Feedback loop |
| ------- | ------------ | ------------- |
| `make build` | Compiles everything (`gradlew :desktop:build`) | compile check — run after any Java change |
| `make run` | Builds if needed, launches the desktop game | runtime smoke test |
| `make sprites` | Recompiles `tools/RebuildAtlas.java`, regenerates atlases + `_low`/`_lowest` pngs from full-size pngs under `assets/field_elements` | tools/asset check |
| `make clean` | Removes gradle build output | — |
| `make validate-ai-docs` | Runs `tools/validate_ai_docs.sh`: manifest paths exist, folder overlays registered, doc links resolve | docs check — run after any docs/AGENTS.md change |
| `./gradlew :desktop:run` | Direct gradle run (needs `JAVA_HOME` set to a JDK) | same as `make run` |
| `./gradlew :desktop:run -PmainClass=yio.tro.antiyoy.desktop.VerifyHarness -PharnessDir=<dir>` | Scripted runtime harness: taps/keys from `<dir>/cmd.txt`, acks + screenshots out | scripted runtime check |

Setup details, JDK install without root, and known warnings: `RUNNING.md`.

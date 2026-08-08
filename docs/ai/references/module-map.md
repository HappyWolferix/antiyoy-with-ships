# Module map

Top-level layout with the overlay `AGENTS.md` of each folder. Load the root `AGENTS.md`
first, then every overlay on the path to the files you touch.

| Folder | Overlay | Contents |
| ------ | ------- | -------- |
| `core/` | `core/AGENTS.md` | The whole game (~1,030 Java files), source root `core/src/yio/tro/antiyoy/` |
| `core/.../gameplay/` | `gameplay/AGENTS.md` | Simulation core: field, hexes, provinces, units, turns, rules, editor, campaign |
| `core/.../gameplay/rules/` | (gameplay overlay) | `GameRules` constants, `Ruleset` implementations (income/upkeep) |
| `core/.../gameplay/data_storage/` | (gameplay overlay) | Save, level-code, legacy encoding |
| `core/.../gameplay/replays/` | (gameplay overlay) | Replay recording/playback |
| `core/.../gameplay/game_view/` | (gameplay overlay) | Match rendering |
| `core/.../gameplay/touch_mode/` | (gameplay overlay) | Input modes (`TmDefault` is normal play) |
| `core/.../gameplay/diplomacy/` | (gameplay overlay) | Diplomacy system (relations, messages, votes) |
| `core/.../gameplay/fog_of_war/` | (gameplay overlay) | Fog-of-war layer |
| `core/.../gameplay/editor/` | (gameplay overlay) | Level editor |
| `core/.../gameplay/tests/` | (gameplay overlay) | In-game debug screens — **not** a unit test suite |
| `core/.../ai/` | `ai/AGENTS.md` | AI opponents; `master/` holds `AiMaster`, the strongest |
| `core/.../menu/` | `menu/AGENTS.md` | All UI — hand-rolled scenes, widgets, renderers |
| `core/.../stuff/` | (core overlay) | Shared utilities: pooling, fonts, scroll engines, languages |
| `core/.../factor_yio/` | (core overlay) | `FactorYio` animatable float, used for all animation |
| `desktop/` | `desktop/AGENTS.md` | Desktop launcher + `VerifyHarness` (additions of this setup; upstream ships none) |
| `tools/` | `tools/AGENTS.md` | Offline tools: `RebuildAtlas.java`, `validate_ai_docs.sh` |
| `assets/` | `assets/AGENTS.md` | Textures, atlases, sounds, fonts, languages |
| `docs/architecture/` | — | Behaviour docs (the spec); see `docs/ai/manifest.yml` |
| `docs/ai/` | — | This AI configuration |

Full class-level map: `docs/architecture/overview.md`.

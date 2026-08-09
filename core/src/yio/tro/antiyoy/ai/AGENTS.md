# ai/ — overlay

AI opponents. Difficulty ladder: `AiEasy` → `AiNormal*` → `AiHard*` → `AiExpert*` →
balancer variants → `master/AiMaster` (strongest, delegates to `AttackManager` /
`DefenseManager`). Most classes come in `*GenericRules` / `*SlayRules` pairs. `AiFactory`
picks the implementation from `Difficulty`.

Read `docs/architecture/ai-opponent.md` first — it lists what the AI understands and,
more importantly, everything it does **not** (its blind spots are documented and partly
intentional difficulty tuning).

Local rules:

- AI unit-building is hardcoded to strengths 1..4; changing tier arithmetic changes AI
  behaviour implicitly.
- AI legality must go through the same gates as the player (`MoveZoneDetection`,
  `Hex.getDefenseNumber()`) — never give the AI a private rules path.

- A province can only buy a unit onto a building-free hex, so anything that builds must go
  through `buildingWouldSealProvince()` or it can seal the province out of the game.

Verify: `make run`, then watch AI-vs-AI or play against the changed difficulty on an
editor-built board; check the AI still finishes turns without stalling.

For balancer/expert changes also run the batch harness, which scores AI strength as share of the
board rather than by win count and asserts no province seals itself in:

    ./gradlew :desktop:run -PmainClass=yio.tro.antiyoy.desktop.AiSkirmishHarness -PappArgs="40,300"

It is not bit-reproducible run to run — treat swings under ~3 points of board share as noise.

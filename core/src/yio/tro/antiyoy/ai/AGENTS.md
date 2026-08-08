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

Verify: `make run`, then watch AI-vs-AI or play against the changed difficulty on an
editor-built board; check the AI still finishes turns without stalling.

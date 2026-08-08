# Boundaries — things never to change casually

- **`Obj` ids are append-only.** Renumbering breaks every existing save and level code.
- **Persistence formats are append-only.** Never add Java serialization. See
  `docs/architecture/persistence.md` before touching `gameplay/data_storage/` or any
  `SavableYio` implementor.
- **`Unit.strength` doubles as a tier id.** Merging caps and AI unit-building are
  arithmetic over the 1..4 range — changing those numbers has non-obvious consequences
  across combat, economy and AI at once.
- **`Hex.getDefenseNumber()` is the combat rule**, not a heuristic: capture legality is
  decided by `RulesetGeneric.canUnitAttackHex` (strength 4 beats everything, otherwise
  attacker strength must exceed the defense number). See `docs/architecture/combat.md`.
- **libGDX stays at 1.9.10.** 1.9.11 changed `InputProcessor.scrolled()` to floats and will
  not compile against this source.
- Do not "fix" known gaps listed in the docs without the user asking — they are accepted
  limitations, documented on purpose.
- Do not commit, push, or rewrite git history unless the user asks.

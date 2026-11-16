# StandGen 0.2.2b (Fabric 1.20.1)

Stands that are generated from **how you play**. Your actions decide your stand's type, look and abilities, and the stand
evolves together with your progress through the game. Inspired by the "stand" idea from JoJo, with no copied characters or assets.

- Requires: **Fabric Loader** + **Fabric API** (the only dependency), Minecraft 1.20.1, Java 17.
- Languages: English, Ukrainian (uk_ua), Russian (ru_ru), German (de_de).

## Getting a stand
- **Craft:** Arrow + Diamond + Gold Ingot (shapeless).
- **Find:** chests in desert pyramids, jungle temples, pillager outposts, shipwrecks, buried treasure, woodland mansions, strongholds,
  village temples, ruined portals, ancient cities, bastions and End cities. Also dropped by evokers, piglin brutes, elder guardians
  and (rarely) wither skeletons, and found as rare fishing treasure.
- **Use:** hold right-click with the Stand Arrow for about 1 second.
- **Multiplayer:** shoot the arrow from a bow or crossbow. The player it hits awakens a stand.
  The arrow is in the `minecraft:arrows` tag, so a bow may pick it from your inventory. Keep it in your off-hand to shoot it on purpose.

## Controls
All keys are rebindable: Options > Controls > Key Binds > **StandGen** (keyboard keys and mouse buttons).
Defaults:
| Key | Action |
|---|---|
| R | Summon / dismiss stand |
| G | Toggle AUTO / MANUAL control |
| Z (hold) | Manual attack: the stand punches where you look |
| X / C / V / B | Skill 1 (soul type) / skill 2 (second type, stage 2) / skill 3 (form, stage 3) / skill 4 (ultimate, stage 4) |

AUTO: the stand fights nearby hostile mobs by itself (small radius, except Swarm) and hits what you hit.
MANUAL: the stand only follows you and attacks while Z is held.

## How it works
- **Soul** = vanilla statistics (blocks mined, kills, blocks placed, distance travelled, bow use, deaths and damage taken).
  This gives a main type and a second type: Miner, Fighter, Builder, Traveler, Archer, Survivor.
- **Stage** = number of completed milestones (iron, diamonds, Nether, fortress, Wither, End, dragon, elytra). Higher stage means higher stats,
  more skills and a more impressive look.
- **Form** (chosen when you awaken, weighted by your soul): Humanoid (+precision), Armor (+durability), Weapon (+power), Swarm (+speed, range).
  Small details (horns, crest, spikes, lamp, halo, quiver...) depend on your type and a seed.
- **16 skills:** 6 by type, 4 by form, 6 ultimates (one per main type).

## Weapon form
- **Auto mode:** the weapon floats beside you and fights on its own.
- **Manual mode (G):** the stand becomes a real tool in your hands: Miner pickaxe, Fighter sword, Builder hammer (a heavy axe),
  Traveler spear, Archer bow (infinite arrows) and Survivor shield (blocks like a shield). Damage scales with the stand's power.
  You need a free hotbar slot. The tool disappears when you switch back to auto mode or dismiss the stand, and it cannot be kept in chests.
- **Dash** range depends on the stand's speed and leaves a trail in your stand's colors along the whole distance.

## Multiplayer
- A player who has no stand **cannot see other players' stands** (the figure, the armor and its particles). Spectators see everything.
- All stand messages (awakening, evolution, `/stand info`) are private to the player. Nothing is broadcast to the server chat.

## Commands
`/stand info` shows your stand. For operators: `/stand awaken`, `/stand reset`, `/stand stage 0..8` (for testing skills).

## For developers
- `StatCollector.register(...)` lets other mods add soul scores. `Progression.addMilestone(...)` adds stage milestones.
- All player-facing text is in `assets/standgen/lang/*.json`. To add a language, copy `en_us.json` and translate it.
- New form: add a constant to `StandForm`, a model in `client/StandModels.java`, and a method in `client/StandRenderer.java`.

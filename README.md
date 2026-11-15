# StandGen 0.2.1 (Fabric 1.20.1)

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
  See "Armor form" below: it is worn by the player instead of being a separate figure.
  Small details (horns, crest, spikes, lamp, halo, quiver...) depend on your type and a seed.
- **16 skills:** 6 by type, 4 by form, 6 ultimates (one per main type).

## Armor form
The Armor stand is real armor worn on your body (all four slots), visible to everyone, tinted with your stand's colors.
- It can only be summoned when **all four armor slots are empty**. While it is summoned you cannot take it off (curse of binding);
  dismiss the stand with R to remove it. It disappears on death and when you leave the game.
- It gives real armor points, toughness and knockback resistance (scaling with the stand's durability and stage),
  and your own hits deal more damage (scaling with power).
- It cannot hit from a distance. Auto mode: it strikes enemies right next to you (about 2.5 blocks). Manual mode: it strikes what you
  look at, up to about 4 blocks, while Z is held. Barrage and Fury Mode are close-range combos.
- Reworked for close range: Piercing Shot (4.5 blocks), Grab (3.5 blocks), Arrow Rain (falls within 8 blocks), Excavate (5 blocks).

## Commands
`/stand info` shows your stand. For operators: `/stand awaken`, `/stand reset`, `/stand stage 0..8` (for testing skills).

## For developers
- `StatCollector.register(...)` lets other mods add soul scores. `Progression.addMilestone(...)` adds stage milestones.
- All player-facing text is in `assets/standgen/lang/*.json`. To add a language, copy `en_us.json` and translate it.
- New form: add a constant to `StandForm`, a model in `client/StandModels.java`, and a method in `client/StandRenderer.java`.

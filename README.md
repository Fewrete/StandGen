# StandGen 0.2.6 (Fabric 1.20.1)

Stands that are generated from **how you play**. Your actions decide your stand's type, look and abilities, and the stand
evolves together with your progress through the game. Inspired by the "stand" idea from JoJo, with no copied characters or assets.

- Requires: **Fabric Loader** + **Fabric API** (the only dependency), Minecraft 1.20.1, Java 17.
- Languages: English, Ukrainian (uk_ua), Russian (ru_ru), German (de_de).

## What's new in this update: elemental stands
- **Four elements: Fire, Water, Ice and Sand.** Some stands awaken with an element. What draws a soul to an element is hidden, so discover it by playing.
  Souls that match no element still have a small chance to get a random one.
- An elemental stand keeps its type skill (slot 1) and its form skill (slot 3). Its second skill (slot 2) becomes an **element ray**
  (Flame Lance, Tidal Jet, Frost Bolt, Sand Blast) and its ultimate (slot 4) becomes an **element field** (Inferno, Deluge, Blizzard, Sandstorm)
  that lasts several seconds around you. Support stands keep their support skills.
- The element tints the stand, adds particles around it and gives a small stat bonus.
- **Soul menu page 2** shows the element and where each of the four skills comes from. Use the `<` `>` buttons or the arrow keys to switch pages.
  In creative or as an operator you can change the element for testing.

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
| K | Open the soul menu |
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

## Stats and the soul menu
Your stand and its stats appear in chat when it awakens and with `/stand info`. The **soul scores** (where your stand's type comes from) live in the soul menu: press **K** or use `/stand menu`.
- **Power:** damage of the stand, its skills and stand weapons. **Speed:** attack rate and shorter skill cooldowns. **Range:** reach and radius.
- **Precision:** critical-hit chance (up to 40%, x1.5 damage) for the stand and stand weapons.
- **Mastery:** improves skills that are not direct attacks (buffs, heals, utility): duration, strength and radius.
- **Durability:** shown only for the Armor form (armor points, toughness, knockback resistance).
- **Testing:** in creative mode (or as an operator) you can edit the soul scores and change the form in the soul menu.

## Multiplayer
- A player who has no stand **cannot see other players' stands** (the figure, the armor and its particles). Spectators see everything.
- All stand messages (awakening, evolution, `/stand info`) are private to the player. Nothing is broadcast to the server chat.

## Commands
`/stand info` prints your stand in chat. `/stand menu` opens the soul menu. For operators: `/stand awaken`, `/stand reset`, `/stand stage 0..8` (for testing skills).

## For developers
- `StatCollector.register(...)` lets other mods add soul scores. `Progression.addMilestone(...)` adds stage milestones.
- All player-facing text is in `assets/standgen/lang/*.json`. To add a language, copy `en_us.json` and translate it.
- New form: add a constant to `StandForm`, a model in `client/StandModels.java`, and a method in `client/StandRenderer.java`.

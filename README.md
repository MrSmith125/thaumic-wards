# Thaumic Wards

A magic-themed server performance optimization and faction/claim system for Minecraft 1.16.5 Forge. Built for heavily modded SMP servers running Thaumaturgy: Mystifying Magic.

## Features

### Server Performance
- **Entity tick reduction** - Reduces entity updates in chunks far from players
- **Chunk load monitoring** - Tracks and limits excessive chunk loading
- **Distant chunk management** - Configurable tick rates for distant chunks

### Chunk Pre-generation
- `/thaumicwards pregen <radius>` - Pre-generate chunks in a spiral from your position
- `/thaumicwards pregen stop` - Stop pre-generation
- Works in singleplayer for pre-generating worlds before server deployment
- Configurable chunks-per-tick to balance speed vs. server load
- Action bar progress display

### Magic World Border
- `/thaumicwards border set <radius>` - Create a magical barrier
- `/thaumicwards border remove` - Remove the barrier
- `/thaumicwards border info` - View border details
- Enchant and portal particle effects visible when approaching
- Push-back force and configurable damage outside the border
- Persists across server restarts

### Area Claiming (Ward Spells)
- **Ward Stone** - Place to claim the chunk you're in
- **Warding Wand** - Select two corners to claim a rectangular area
- `/thaumicwards claim` / `unclaim` / `claims` - Command-based claiming
- Magic forcefield particles at claim boundaries
- Full protection: block break/place, interaction, explosions, entity damage
- Enchant particles for personal claims, witch particles for guild claims

### Faction Guild System
5-tier rank system with progression:

| Rank | Abilities |
|------|-----------|
| **Apprentice** | Enter guild hall, basic interaction |
| **Journeyman** | Claim personal chunks in faction territory |
| **Adept** | Invite new members |
| **Master** | Expand guild claims, place Guild Nexus |
| **Archon** | Full admin - promote, demote, kick, disband |

#### Faction Commands
```
/thaumicwards faction create <name>   - Create a new guild
/thaumicwards faction disband         - Dissolve your guild (Archon only)
/thaumicwards faction invite <player> - Invite a player (Adept+)
/thaumicwards faction accept          - Accept a guild invitation
/thaumicwards faction leave           - Leave your guild
/thaumicwards faction kick <player>   - Expel a member (Master+)
/thaumicwards faction promote <player>- Promote a member (Archon only)
/thaumicwards faction demote <player> - Demote a member (Archon only)
/thaumicwards faction info [name]     - View guild details
/thaumicwards faction list            - List all guilds
/thaumicwards faction claim           - Claim chunk for guild (Master+)
/thaumicwards faction unclaim         - Release guild chunk (Master+)
```

### Magic Items & Blocks
- **Ward Stone** - Personal claim marker block with spiraling enchant particles
- **Guild Nexus** - Faction claim marker with dramatic double-helix particle effects
- **Warding Wand** - Rectangle area selection tool for bulk claiming
- **Faction Sigil** - Displays guild info on use

## Configuration

All settings are configurable via the server config file generated at `world/serverconfig/thaumic_wards-server.toml`:

- Performance: chunk load limits, entity tick reduction, distant chunk thresholds
- Pre-generation: chunks per tick, maximum radius
- Border: warning distance, damage outside border
- Claims: max personal claims, guild claim limits, claim expiry
- Factions: max name length, max members

## Recipes

| Item | Recipe |
|------|--------|
| Ward Stone | Obsidian ring + Ender Pearl + Nether Star |
| Guild Nexus | Diamonds + Ender Pearls + Nether Star |
| Warding Wand | Blaze Rods + Ender Eye |
| Faction Sigil | Gold Ingots + Ender Eye |

## Building

Requires Java 8 JDK.

```bash
./gradlew build
```

The built jar will be in `build/libs/`.

## Installation

1. Install Minecraft Forge 1.16.5 (36.2.39+)
2. Place the jar in your `mods/` folder
3. Start the server to generate the config file
4. Configure settings in `world/serverconfig/thaumic_wards-server.toml`

## License

All rights reserved.

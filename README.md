# AntiSeedCracker

[![Build](https://github.com/SunsetRQ/AntiSeedCracker/actions/workflows/build.yml/badge.svg)](https://github.com/SunsetRQ/AntiSeedCracker/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/SunsetRQ/AntiSeedCracker)](https://github.com/SunsetRQ/AntiSeedCracker/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange)](https://adoptium.net/)

**Version:** 3.3.0 &nbsp;|&nbsp; **Target:** Minecraft 26.1.2 &nbsp;|&nbsp; **Author:** SunsetRQ7_

AntiSeedCracker is a professional, multi-layer world seed protection plugin for Paper, Purpur, and Folia. It blocks every known technique a player can use to recover your world seed, protecting your server from ESP clients, ghost clients, and seed-cracking tools such as SeedCrackerX.

---

## Features

| Protection Layer | Description |
|---|---|
| **Login & Respawn Packet Spoofing** | Replaces the hashed seed in `JOIN_GAME` and `RESPAWN` network packets with a per-player cryptographically random fake seed. Operates at the Netty I/O level via PacketEvents before any client ever receives the real value. |
| **Dynamic Seed Rotation** | Periodically assigns each online player a new random fake seed (configurable interval, minimum 30 s). Only affects the network-facing hashed seed, never the generation seed. Defeats crackers that accumulate packets across multiple sessions. |
| **Seed Integrity Monitor** | Read-only watchdog that compares each world's live generation seed against its value at startup and warns in the console if it ever drifts. Never writes to the seed itself — see [Correctness & Large-Server Notes](#correctness--large-server-notes) for why. |
| **Eye of Ender Redirection** | Intercepts thrown Eyes of Ender and redirects their velocity toward a per-player fake stronghold location. Applies a ±8° angular jitter to prevent exact triangulation across repeated throws. |
| **/locate Spoofing** | Cancels `/locate` and replies with convincingly fake coordinates. Stronghold queries use the player's stable fake location for consistency. |
| **/seed Command Block** | Blocks `/seed`, `/minecraft:seed`, `/getseed`, `/worldseed`, and any extra commands configured in `config.yml` for players, console, and RCON. Also strips seed commands from tab-complete results. |
| **End Spike Randomization** | Shuffles the bedrock cap heights of all 10 End obsidian pillars at world load and after each dragon respawn cycle. Breaks the vanilla height fingerprint `{76, 79, 82 … 103}` that uniquely identifies the seed. |
| **End City Glass Replacement** | Replaces magenta stained glass in End City chunks (scanning only the structure's bounding box, not the full chunk column) with purpur blocks on first load. Prevents long-range visual fingerprinting of the distinctive glass pattern. |
| **Treasure & Explorer Map Scrambling** | Intercepts `MAP_DATA` packets and randomises the on-map position of every structure icon (buried treasure, woodland mansion, ocean monument, and others). |
| **Slime Chunk Obfuscation** *(opt-in)* | Cancels natural slime spawns in configured worlds so tools sampling spawn/no-spawn per chunk get no signal. Disables slime farms in those worlds — off by default. |
| **Structure Reconnaissance Monitor** | Detects (logs only, never blocks) when a player visits an unusual number of seed-cracking-relevant structures in a short window — see [Threat Model](#threat-model-liquidbounce--seedcrackerx-research) below. |
| **Audit Log** | Appends every blocked command, spoofed packet, redirected Eye, and recon flag to a flat-file TSV log in `plugins/AntiSeedCracker/logs/`. Zero external dependencies — no SQLite. |

---

## Compatibility

| Platform | Supported | Verified |
|---|---|---|
| Paper 26.1.2 | ✔ | ✔ Live-tested on build 74, zero errors, seed integrity verified byte-for-byte on disk |
| Folia 26.1.2 | ✔ (region-threaded scheduling) | ✔ Live-tested on build 8, zero errors, seed integrity verified byte-for-byte on disk |
| Purpur 26.1.2 | ✔ (Paper-compatible) | — |
| Spigot / CraftBukkit | ✘ Not supported — the plugin uses Paper-only APIs (Adventure, AsyncScheduler) | — |

**Runtime:** Java 25 or higher is required.

## Metrics

Anonymous usage statistics are collected via [bStats](https://bstats.org/plugin/bukkit/AntiSeedCracker/32378). This can be disabled globally in `plugins/bStats/config.yml`.

---

## Installation

1. Download `AntiSeedCracker-3.3.0.jar` from [Releases](../../releases/latest).
2. Place it in your server's `plugins/` folder.
3. Start or restart the server.
4. Edit `plugins/AntiSeedCracker/config.yml` to your preference.
5. Run `/asc reload` to apply changes without restarting.

---

## Commands

| Command | Description | Permission |
|---|---|---|
| `/asc reload` | Reloads config and restarts all protection tasks | `antiseedcracker.admin` |
| `/asc status` | Shows which protection layers are active | `antiseedcracker.admin` |
| `/asc stats` | Displays aggregate event counts from the audit log | `antiseedcracker.admin` |
| `/asc info` | Shows version, author, platform, and update status | `antiseedcracker.admin` |

The `antiseedcracker.admin` permission defaults to server operators.

---

## Configuration

```yaml
seed_obfuscation:
  intercept_login: true
  intercept_respawn: true

seed_rotation:
  enabled: true
  interval_seconds: 60

structure_protection:
  end_spikes:
    enabled: true
    worlds:
      - world_the_end
    modify_world: true
  end_cities:
    enabled: true
    worlds:
      - world_the_end
    modify_world: true
  spoof_locate_command:
    enabled: true
    max_offset: 2000

seed_integrity_monitor:
  enabled: true

slime_chunk_obfuscation:
  enabled: false
  worlds: []

structure_recon_monitor:
  enabled: true
  threshold: 6
  window_seconds: 900

eye_of_ender_protection:
  enabled: true
  worlds:
    - world
  fake_stronghold_min_distance: 800
  fake_stronghold_max_distance: 4000

update_checker:
  enabled: true
  modrinth_id: "YOUR_MODRINTH_PROJECT_ID"

database:
  enabled: true
  log_events: true
  max_event_age_days: 30

treasure_map_protection:
  enabled: true

messages:
  seed_blocked_player: "&c[AntiSeedCracker] Access to world seed information is restricted."
  seed_blocked_console: "&c[AntiSeedCracker] Seed access is restricted. The real seed is never exposed."

extra_blocked_commands:
  - "seedcracker"
  - "seedfind"
  - "worldseedinfo"
```

### `modify_world` Warning

Setting `modify_world: true` for End spikes or End cities **permanently changes world data**. Spike bedrock caps are moved to shuffled positions and End City magenta glass is replaced with purpur blocks. These changes persist across restarts. Set to `false` to disable physical world modifications while keeping all other protection layers active.

---

## World Safety

AntiSeedCracker never reads, stores, or logs the real world seed. The only world modifications performed are:

- Moving bedrock cap blocks on End obsidian pillars (when `end_spikes.modify_world: true`).
- Replacing magenta stained glass in End City chunks with purpur blocks (when `end_cities.modify_world: true`).

Both modifications use `physicsUpdate: false` to prevent block-update cascades. Each chunk is tagged with a Persistent Data Container key so the modification runs **at most once per chunk** across all server restarts. No terrain, biome, or structure data outside these specific block types is ever altered.

**AntiSeedCracker never writes to a world's generation seed, at any point, for any reason.** Earlier versions did (see below) — this caused real damage and has been removed entirely.

---

## Correctness & Large-Server Notes

**Version 3.3.0 fixes a serious bug present in 3.2.0 and earlier.** The old "Plugin API Protection" feature patched the live in-memory seed field that Minecraft's own chunk generator reads, once per second, forever, on every loaded world. That field isn't a copy — it's the exact same value the vanilla decoration step (trees, ores, ravines, structure placement) re-reads *fresh for every chunk it generates*, not just once at world load. The result: any chunk decorated while that field held a fake value got seeded from randomness instead of your real seed, causing visibly broken terrain/structure generation, and the constant reflection-based writing added avoidable overhead and cross-plugin risk on busy servers.

This has been completely removed. In its place, `SeedIntegrityMonitor` only *reads* `world.getSeed()` on a low-frequency timer and logs a warning if it ever changes — it never writes to it. This was verified by booting real Paper 26.1.2 and Folia 26.1.2 servers with explicit known seeds and confirming, at the raw NBT byte level in `world_gen_settings.dat`, that the seed never drifted after extended runtime.

Other large-server changes in this release:
- `EndCityProtector` now scans only the generated structure's bounding box instead of the entire chunk column (previously up to 256 vertical blocks × 256 columns per End City chunk).
- The periodic player-seed safety-net task runs every 15s instead of every 1s (it no longer has any seed-patching work to do).

---

## Threat Model: LiquidBounce & SeedCrackerX Research

This release includes research into how modern cheat clients and cracking tools actually recover seeds, so protections target real techniques rather than guesses:

- **SeedCrackerX** (the dominant seed-cracking tool) primarily works by combining the *exact coordinates* of structures the player has legitimately found — shipwrecks, desert pyramids, jungle temples, swamp huts, pillager outposts, ocean monuments, igloos, and End Cities — until it has enough bits to brute-force the seed. **This is not a network exploit.** A player standing at a real structure already knows their own coordinates; no plugin can hide a player's position from themselves without breaking movement entirely. This is a fundamental limitation of any server-side plugin, not just this one — treat any tool's claim of "100% uncrackable" against this technique with skepticism.
- What we added because of this: `StructureReconMonitor` watches for the specific pattern SeedCrackerX data-gathering produces — visiting several of those exact structure types in a short time window — and logs it for admin review. It never blocks or punishes, matching this plugin's design; it just gives you visibility into likely automated cracking activity.
- **Slime-chunk sampling** is another real technique (spawn/no-spawn per chunk leaks a bit of seed information). `slime_chunk_obfuscation` (opt-in, off by default) cancels natural slime spawns in configured worlds to deny this signal — at the cost of disabling slime farms there.
- **LiquidBounce's `XRay` and `StorageESP` modules** (block/ore/chest ESP) are a **different threat entirely** — they exploit full chunk data the server must send for rendering, and have nothing to do with the world seed. This is not something a seed-protection plugin should attempt to solve by reimplementing chunk obfuscation (high risk, likely to break vanilla behavior — exactly what large-server admins told us to stop doing). **Use Paper's built-in anti-xray** (`anticheat.anti-xray` in `paper-world-defaults.yml`) for that threat class; it's mature, maintained, and the correct tool for the job.
- Packet-level hashed-seed spoofing (login/respawn) and `/locate` spoofing still close off the older, simpler leak vectors that predate SeedCrackerX's structure-based method.

---

## Folia Threading Model

All block mutations are dispatched to the region thread owning the relevant chunk via `RegionScheduler` (Folia) or the main thread (Paper). Packet interceptors run on the Netty I/O thread and only perform `ConcurrentHashMap` lookups. The seed rotation and player-seed safety-net tasks run on the `AsyncScheduler` (Paper/Folia).

---

## Known Bypass Mitigations

| Attack Vector | Mitigation |
|---|---|
| Login/Respawn packet interception | PacketEvents HIGHEST-priority intercept |
| `/seed`, `/getseed`, `/worldseed` | `PlayerCommandPreprocessEvent` + `ServerCommandEvent` + `RemoteServerCommandEvent` blocking |
| Eye of Ender triangulation | Per-player fake stronghold + ±8° jitter |
| `/locate structure` triangulation | Spoofed coordinate output, consistent per-player stronghold fake |
| Treasure / explorer maps | `MAP_DATA` packet decoration scrambling |
| End spike height fingerprinting | Full Fisher-Yates shuffle of pillar cap heights |
| End City visual fingerprinting | Magenta glass block replacement (bounding-box scoped) |
| SeedCrackerX-style structure recon | Detection + audit logging (cannot be prevented — see Threat Model) |
| Slime-chunk seed sampling | Optional natural-spawn cancellation |
| Tab-complete exposure | `TabCompleteEvent` filtering |
| RCON seed commands | `RemoteServerCommandEvent` blocking |
| Block/ore ESP (XRay, StorageESP) | Out of scope — use Paper's native anti-xray |

---

## Building from Source

Requires Maven 3.6+ and **JDK 25**. The project compiles against folia-api 26.1.2 (Java 25 class files) and produces a Java 25 output jar.

```sh
# Windows
set JAVA_HOME=C:\Program Files\Java\jdk-25.0.3
mvn clean package

# Linux / macOS
export JAVA_HOME=/path/to/jdk-25
mvn clean package
```

The shaded jar is produced at `target/AntiSeedCracker-3.3.0.jar`. PacketEvents 2.13.0 and bStats 3.2.1 are bundled and relocated under `me.legendcraft.antiseedcracker.lib`.

---

## Design Principles

- **Never punish players.** AntiSeedCracker never kicks or bans anyone — it silently feeds cheat clients useless data.
- **Never touch the real seed.** The plugin never reads, stores, transmits, or logs the actual world seed.
- **Never corrupt the world.** All optional world modifications are single-block, physics-free, idempotent, and clearly documented.
- **Fully asynchronous.** Packet work happens on Netty I/O threads; periodic tasks run on async schedulers; block mutations are dispatched to the owning region thread on Folia.

---

## License

Released under the [MIT License](LICENSE). Free to use, modify, and redistribute.

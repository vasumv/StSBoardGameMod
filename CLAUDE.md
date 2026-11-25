# Project notes

Fork of LuaViper/StSBoardGameMod (MIT), a Slay the Spire 1 mod that implements
Slay the Spire: The Board Game. Goal of this fork: add networked multiplayer.

## Goal

- Each remote player controls one character in a shared board-game run (2-4 players).
- Play style: everyone plays simultaneously (like Slay the Spire 2 co-op); plays are
  serialized into one shared queue and take effect immediately. A history panel lets
  players roll back to before any play made this turn. No batches or commit step.
- Follow the board game's actual multiplayer rules: see `docs/MULTIPLAYER_RULES.md`.
- Do not anchor on the existing `multicharacter` code (below). If a cleaner design
  works properly, use it; treat that code as reference only.

Design and milestones: `docs/MULTIPLAYER_DESIGN.md` (each player runs a solo
game, shared state is synced, plays go through one shared queue, rollback via
snapshots).

## What exists

- No networking code anywhere in the mod.
- `src/main/java/BoardGame/multicharacter/` (~4k lines): the original author's
  unfinished mode where one person controls up to 4 characters in board-game rows.
  Hidden behind the "test features" toggle in the mod config
  (`BoardGame.ENABLE_TEST_FEATURES`). Not yet tested in game by us; last substantial
  upstream work on it was mid-2024.
- The upstream README lists known softlocks and rule inaccuracies.

## Build

Targets Java 8. Compiles against jars from a local Steam install, so it only
builds on a machine with Slay the Spire 1 plus these Workshop mods subscribed:
ModTheSpire, BaseMod, StSLib, Bestiary, Quick Restart. Paths are in `pom.xml`.

    nix-shell --run "mvn package"

The build copies `target/BoardGame.jar` into the game's `mods/` folder. Disable the
Workshop copy of the mod so it does not load twice. A cloud/remote session without
the game jars can read and edit code but cannot compile or run it.

## Running without Steam / headless

`scripts/run-sandbox.sh p1 --headless` launches the built mod from an isolated
folder (`../sandbox/p1`, own saves) on a hidden virtual display. Use different
names for several instances. Log output goes to stdout.

## Releases

Built jars are published as GitHub releases on vasumv/StSBoardGameMod (tag
`mp-YYYY.MM.DD`, marked pre-release) with `gh release create <tag> target/BoardGame.jar
--target multiplayer`. Players download `BoardGame.jar` from there.

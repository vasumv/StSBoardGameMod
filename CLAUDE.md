# Project notes

Fork of LuaViper/StSBoardGameMod (MIT), a Slay the Spire 1 mod that implements
Slay the Spire: The Board Game. Goal of this fork: add networked multiplayer.

## Goal

- Each remote player controls one character in a shared board-game run (2-4 players).
- Play style to support: players "batch" their card plays, commit the batch, and
  can undo (roll back) a committed batch. A batch applies in a defined order, with
  animations playing as each action resolves. Rollback is the main requirement.
- Follow the board game's actual multiplayer rules. These have not been researched
  yet; do that before designing.

## What exists

- No networking code anywhere in the mod.
- `src/main/java/BoardGame/multicharacter/` (~4k lines): the original author's
  unfinished mode where one person controls up to 4 characters in board-game rows.
  Hidden behind the "test features" toggle in the mod config
  (`BoardGame.ENABLE_TEST_FEATURES`). Not yet tested in game by us; last substantial
  upstream work on it was mid-2024. This is the intended base for multiplayer.
- The upstream README lists known softlocks and rule inaccuracies.

## Build

Targets Java 8. Compiles against jars from a local Steam install, so it only
builds on a machine with Slay the Spire 1 plus these Workshop mods subscribed:
ModTheSpire, BaseMod, StSLib, Bestiary, Quick Restart. Paths are in `pom.xml`.

    nix-shell --run "mvn package"

The build copies `target/BoardGame.jar` into the game's `mods/` folder. Disable the
Workshop copy of the mod so it does not load twice. A cloud/remote session without
the game jars can read and edit code but cannot compile or run it.

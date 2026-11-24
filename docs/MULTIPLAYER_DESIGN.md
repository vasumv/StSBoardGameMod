# Multiplayer design

Status: milestones 1 and 2 are done (test harness; undo in solo play, tested on basic
Ironclad cards only). Networking has not been started. Rules reference:
`MULTIPLAYER_RULES.md`.

## What the code investigation found

- Solo play is complete: one real character per game, with all rewards, events,
  shop, campfire and saves working.
- The upstream `multicharacter` mode puts several characters in one game by
  swapping the global `AbstractDungeon.player` around every action. By the author's
  own in-game warning it manages "one or two hallway encounters"; nothing outside
  combat exists for it (rewards, decks, gold, shop, events, death, saves), and
  about 350 content classes read the global player directly.
- Combat randomness is all drawn from the game's seeded generators. No gameplay
  logic uses unseeded randomness.
- There is no snapshot or save-state code, and saves do not capture the die or
  several board-game relic fields.
- The game jar bundles Steam lobbies and Steam peer-to-peer networking.

## Decision 1: every player runs their own solo game; shared things are synced

Each player's game holds exactly one character, as solo play does today. The mod
adds a session layer that keeps the shared parts identical across players.

Why not several characters in one game (the upstream approach): it needs the
fragile global-player swap across hundreds of classes, plus a from-scratch rewrite
of everything outside combat. In the board game almost everything outside combat
is per-player anyway, so solo games already do the right thing.

Shared and synced:
- Seed, map, party position, room contents (encounter per row, event, merchant
  stock, relic/potion/boss-relic deck draws). The host owns the shared decks.
- The die: the host rolls, everyone uses that result.
- Enemies: every game spawns the enemies of all rows. After each resolved card and
  each enemy action, the acting side's enemy state (HP, block, powers, intent, cube
  position, deaths, summons) is sent to the others.
- A small status panel per other player: HP, block, energy, powers, row.

Local to each player: hand, piles, energy, relics, potions, gold, own rewards.

Cross-player effects ("any player" block, potion gifts, paying toward a purchase)
are small messages applied in the target player's game.

Enemy turn: every game runs each enemy's action in the rulebook order. Effects
aimed at the player are suppressed unless the enemy is in that player's row, is a
Boss, or the action is area-of-effect. Enemy-side results are then synced from the
row owner. Which non-Boss actions are area-of-effect is not in the code today and
has to be added per monster from the physical cards.

## Decision 2: one player acts at a time ("the floor"); a batch is a floor session

- Playing a card takes the floor if it is free. While someone holds the floor the
  others cannot play, but watch each card resolve with its animation.
- A batch is everything a player does while holding the floor. Committing a batch
  releases the floor.
- Group decisions (die modifiers, end turn, map path, row switching) need every
  player to confirm.

This matches the rules (any order, each card resolves fully) and removes the case
of two players changing the same enemy at once.

## Decision 3: undo by generic in-memory snapshots

Before each card play and at each batch boundary, every game records a snapshot of
its own combat state; snapshots are numbered the same in every game. Undo tells all
games to restore the same numbered snapshot.

A snapshot is content-agnostic: walk the object graph from the player, the
enemies, the action manager, the random generators and the mod's static fields,
and record a shallow copy of every field of every object reached (collections by
content). Restoring writes the values back into the same objects. This avoids
per-card/per-power code for 900 classes. Rendering resources (textures, skeletons,
sounds) are skipped.

Rules for undo:
- Only when the action queue is idle, and only within the current player turn.
- Last-in first-out: undoing an earlier batch also undoes the later ones.
- Random generators are restored too, so redoing gives the same draws and rolls.
- "Reset turn" restores the start-of-turn snapshot.

Known limits: cosmetic state inside skipped rendering objects (e.g. an animation
that changed) may not rewind.

## Transport

A small message layer with two back ends: plain TCP (direct connect, and how two
local instances are tested) and Steam lobbies + peer-to-peer (invite friends, no
port forwarding). Steam cannot be tested with one account, so TCP comes first.

## Milestones

1. Test harness: run isolated game instances without Steam, on a hidden display
   (`scripts/run-sandbox.sh`), plus a debug command channel to drive them.
2. Undo in solo play (snapshot/restore, undo card, reset turn). Useful by itself.
3. Session layer over TCP: lobby, shared seed, map movement, room sync.
4. Multi-row combat: enemy sync, the floor, networked undo, enemy-turn rules.
5. Shared out-of-combat pieces: merchant stock, boss relics, potion trading,
   paying for others, row switching, defeat rules, "any player" card targets.
6. Steam lobbies. Token limits and area-of-effect data per monster.

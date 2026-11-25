# Multiplayer design

Status (2026-11-25): playable end to end in Act 1 between two players; Acts 2-3
untested. Rules reference: `MULTIPLAYER_RULES.md`. Audits against the sts-tbg
reference implementation: `reference/enemy_audit.md`, `reference/enemy_mp_data.json`.

## How a shared fight works now

- Rows: one encounter per player row (normal and forced event fights); the Elite
  in the bottom row with the rest of its group spread over the other rows; the
  Boss in every row. Summons go to the summoner's row (Elite/Boss summons are
  spread over the rows). Elite/Boss HP comes from the card's HP board for the
  number of players (`combat/EnemyMPData`, generated from the reference data).
- Targeting: enemies hit the player in their row; Bosses and moves marked as
  "all players" on the card hit everyone.
- Screen: your row is the normal battlefield; each teammate has a panel along the
  top with their character, HP/block/energy/hand/ready and their row's enemies
  drawn small (targetable). Drag an "any player" card onto a teammate's panel to
  give them its effect.
- Die: one roll per round, shown to everyone. Anyone may change it with a die
  ability; the change is shared and everyone confirms again. When everyone has
  confirmed, die relics trigger on the final number. No card plays before that.
  The solo Loaded Die ability is off.
- Play queue and rollback: as below. Enemy changes outside the queue (potions,
  end/start-of-turn effects, reactions such as Thorns during the enemy turn) are
  shared as additive deltas. Poison ticks once per round (host).
- Outside combat: party votes on the map (most votes wins, ties by the die);
  rows switch when both players ask; potions can be given; gold can be paid toward
  a teammate's purchase at a shop or event; a death ends the run for everyone.
- Merchant: one stock for the party (the host's 3 relics and 3 potions); an item
  one player buys is gone for everyone. Card offers and card removal stay per player.
- Boss chest: one boss relic per player + 1; a relic one player takes disappears for
  the others.
- Keys: Ruby (everyone recalls at the same campfire) and Sapphire (everyone takes the
  key at the same chest) are granted only when the whole party does it.
- Events: Lab and Dead Adventurer use one party roll; the Lab's extra potion goes to
  one player (bottom row); Big Fish options must all be different.
- Lobby: main menu "Multiplayer" button: host or join (type or paste the address),
  pick a character; the host picks the ascension level and starts the run.

## Known gaps

- Everything since the Act 1 walk-through is compile-checked only (not run in game):
  Steam lobbies, reconnect, save/continue, shared merchant, boss relic count, keys,
  party events (Neow start, Lab, Dead Adventurer, Big Fish, Ancient Temple), relic deck
  removals, acts-last, per-player enemy values, Sentries per row, Awakened One
  Cultists, partial all-player hits, new enemy cards (Snake Plant A7, Mugger, Mystic,
  Taskmaster slavers), optional rules (Last Stand, Choose Your Relic, Sequential Turns).
  Acts 2-3 have not been played in multiplayer.
- Steam: needs two Steam accounts to verify. Accepting an invite while the game is closed
  may not join automatically (ModTheSpire may not pass +connect_lobby); start the game
  first, then accept.
- Sequential Turns is approximate: turn order and who may act follow the rule, but
  everyone's start-of-turn draw still happens at the start of the round.
- The host is the hub: if the host disconnects, the run cannot continue (clients
  reconnect to the host, not the other way round). Use Continue saved run.

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

## Decision 2: everyone plays at once; plays go through one shared queue

(Replaces an earlier "one player holds the floor" idea, which the project owner
rejected in favour of Slay the Spire 2 style simultaneous play.)

- Any player may play a card, potion or ability at any time during the player
  turn. There are no batches and no commit step.
- Each play is a request to the host, which gives it the next sequence number.
  Plays resolve strictly in sequence order, one at a time across all players, each
  fully (with its animation, shown to everyone) before the next starts. The rules
  require full resolution per card, and it means two players never change the same
  enemy at once.
- A play is validated when it reaches the front of the queue, in the acting
  player's game. If it is no longer legal (target dead, not enough energy), it is
  dropped, the card stays in hand and the player is told.
- A player may queue several plays; cards that only enter the hand later (draws)
  can be played once they arrive.
- Group decisions (die modifiers, end turn, map path, row switching) need every
  player to confirm.

## Decision 3: roll back to before any play in the queue

Every game records a snapshot of its own combat state before each queued play
resolves, numbered by the play's sequence number, plus one at the start of the
turn. A history panel lists the plays resolved this turn (who, what, target).
Choosing an entry restores, in every game, the snapshot taken before that play, so
that play and everything after it (from any player) is undone and the cards are
back in hand.

A snapshot is content-agnostic: walk the object graph from the player, the
enemies, the action manager, the random generators and the mod's static fields,
and record a shallow copy of every field of every object reached (collections by
content). Restoring writes the values back into the same objects. This avoids
per-card/per-power code for 900 classes. Rendering resources (textures, skeletons,
sounds) are skipped. This part is implemented and works in solo play
(`BoardGame.multiplayer.undo`).

Rules for rollback:
- Only within the current player turn; history is cleared when the turn ends.
- Requested through the same queue, so it is ordered against in-flight plays;
  plays queued behind a rollback are dropped.
- Any player may propose a rollback from the history panel. Everyone else gets a
  prompt showing who asked and which play; it happens once every player accepts and
  is cancelled if anyone declines. New plays are held while a proposal is open. In
  solo play it is immediate.
- Random generators are restored too, so replaying gives the same draws and rolls.

Known limits: cosmetic state inside skipped rendering objects (e.g. an animation
that changed) may not rewind; the killing blow that ends a fight cannot yet be
undone.

## Transport

A small message layer with two back ends: plain TCP (direct connect, and how two
local instances are tested) and Steam lobbies + peer-to-peer (invite friends, no
port forwarding). Steam cannot be tested with one account, so TCP comes first.

## Milestones

1. Test harness: run isolated game instances without Steam, on a hidden display
   (`scripts/run-sandbox.sh`), plus a debug command channel to drive them.
2. Undo in solo play (snapshot/restore, undo card, reset turn). Useful by itself.
3. Session layer over TCP: lobby, shared seed, map movement, room sync.
4. Multi-row combat: enemy sync, the shared play queue, history panel and networked
   rollback, enemy-turn rules.
5. Shared out-of-combat pieces: merchant stock, boss relics, potion trading,
   paying for others, row switching, defeat rules, "any player" card targets.
6. Steam lobbies. Token limits and area-of-effect data per monster.

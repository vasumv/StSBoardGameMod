# Board game rules that matter for multiplayer

Source: the Kickstarter rulebook PDF (`STS_KS_Rulebook.pdf`, 24 pages, dated
2024-03-23). Page numbers below refer to it. Later printings/errata may differ;
the mod's changelog mentions rule updates after this date (e.g. Reflex/Tantrum),
so check card-level rulings against the mod before trusting this file.

## Win / lose

- Any player reaching 0 HP ends the game in defeat for the whole party (p2, p13).
- Optional "Last Stand": in a Boss fight the others keep fighting; enemies in a dead
  player's row target the nearest row with a player below, else above. A survivor
  counts as beating the Act but the party cannot continue to the next Act (p23).

## Combat setup (p10-11)

- Each player has a row. Enemies sit in rows, except Bosses.
- Encounter: one encounter card per player, one enemy per row. Summons go in the
  row of the enemy that summoned them.
- Elite: one Elite, placed in the bottom row. Its Summons go one per player row.
- Boss: treated as being in all rows. Boss HP depends on player count (HP board).
- Act I first room uses a "1st Encounter!" card in each row.

## Combat round (p12-13)

One shared Player Turn, then the Enemy Turn. All players take the Player Turn
steps simultaneously.

Start of turn:
1. Reset: Energy to 3, Block to 0.
2. Draw 5. No hand limit. Empty draw pile: shuffle discard into a new draw pile.
3. Roll the die once. Abilities that change the result must be used now, before
   anything else resolves. One result drives every die effect for the round
   (monster die actions and die relics).
4. Start-of-turn, start-of-combat and die-relic abilities trigger, in any order the
   players choose.

Play:
- Players play cards, use potions and activate abilities in any order they choose.
  Open discussion is expected.
- Playing a card: pay, choose targets, resolve text top to bottom, then clean up.
  Abilities triggered by the card wait until the card has fully resolved.
- While being played a card is in neither hand nor discard.

End of turn (once all players are done):
1. End-of-turn abilities trigger in any order the players choose.
2. Everyone discards their hand (Retain excepted; Ethereal exhausts).

Enemy turn:
1. Enemies lose leftover Block.
2. Enemies act: highest row first, left to right within a row, then the next row
   down. Bosses act last, as do enemies marked "acts last".
3. Cube-action enemies advance their cube.

## Targeting (p12, p15, p24)

- A player may target any enemy in any row.
- Player area effects (AoE icon / "any row") hit all enemies in one chosen row and
  always include the Boss. "ALL enemies" hits every row and the Boss.
- Enemies target the player in their own row. Enemy AoE actions hit every player.
  An enemy gaining Strength or Block always puts it on itself.

## Limits (p18-19, p24)

- Per player: Energy 6, Block 20, Strength 8, Vulnerable 3, Weak 3, potions 3.
- Shivs max 5, Miracles max 5. Poison max 30 combined across all enemies.
- Tokens used via another character's cards share that character's limit across
  all players.
- When a limit is hit the extra gain is ignored (a Shiv may be used immediately).
  The upstream README says token limits are not implemented in the mod.

## End of combat and rewards (p8, p13)

- Combat ends at once when all enemies are dead or gone, unless an on-death summon
  is pending.
- Encounters: each player takes the rewards of the enemy in the row they ended
  combat in. Elite and Boss: every player gains the rewards.
- Rewards may be taken in any order or skipped, with full knowledge of the other
  rewards from that room before committing.
- Boss relic: reveal players + 1 (3 when solo); each player may take one.
- Players may switch rows between combats.

## Outside combat (p8-9)

- One party marker on the map; the party moves together.
- Event: each player picks their own bracketed option, acting once, in any order.
  Some events say otherwise on the card.
- Campfire: each player chooses Rest (heal 3) or Smith (upgrade a card).
- Treasure: each player gains a relic.
- Merchant: 3 relics and 3 potions shared by the party; each player reveals 3 of
  their own reward cards; one card removal per player.
- Trading: only potions, and only outside combat. Gold cannot be handed over, but
  a player may pay any part of another player's purchase at a Merchant or Event.
- Acts II+: everyone heals to full at the start of the Act.
- Act IV keys need unanimous choices (Ruby: nobody acts at a campfire; Sapphire:
  everybody skips the relic) (p22).

## Optional rules (p23)

- Sequential Turns: players take full Player Turns one after another, bottom row
  first; the die is rolled once per round.
- Choose Your Relic: at Elite/Treasure reveal one relic per player and let each
  player choose.
- Last Stand: see above.

## What this means for batched play with undo

- The rules already allow any interleaving of plays within the shared turn, so a
  "batch" (one player's ordered list of plays, committed together) is legal as long
  as each card in it resolves fully before the next starts.
- Shared decision points that need agreement, not one player's input: die-result
  modifiers right after the roll, ordering of start/end-of-turn abilities when it
  matters, ending the turn, the map path, row switching, shared rewards (Boss
  relics, merchant stock).
- Undo has to cope with hidden information that a batch revealed (cards drawn,
  shuffles, random targets). Default assumption: an undone-then-replayed action
  gives the same random results, so undo cannot be used to reroll.

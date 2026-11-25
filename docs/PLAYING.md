# Playing multiplayer

## Install (every player)

1. Slay the Spire on Steam. Subscribe on the Workshop to ModTheSpire, BaseMod and StSLib.
2. Unsubscribe from (or untick) the Workshop "The Board Game" mod.
3. Put `BoardGame.jar` from this branch's build in the game's `mods` folder
   (Steam: right-click the game > Manage > Browse local files; create `mods` if missing).
   Everyone must use the same jar.
4. Launch with "Play With Mods" and tick BaseMod, StSLib and BoardGame only.

## Connect

- Main menu, top right: **Multiplayer**.
- **Different networks (recommended): Steam.** Everyone starts the game through Steam.
  The host clicks **Host Steam lobby**; the Steam overlay opens to invite friends (also
  **Invite Steam friends** in the lobby). Friends accept the invite in Steam while the game
  is running at the main menu, and they join automatically. Steam connects you across
  networks (relaying if needed); nobody has to open ports.
- **Same network / Tailscale / forwarded port:** the host clicks **Host on port 7777**; the
  panel lists their addresses. Others type or paste the address (`192.168.1.20:7777`) and
  **Join**.
- Everyone picks a character. The host picks the ascension level and the optional rules
  (Last Stand, Choose Your Relic, Sequential Turns) and clicks **Start run**.
- **Continue saved run**: the party is saved at every room. After quitting, everyone opens
  the lobby again; the host clicks **Continue saved run** and everyone loads their own save.
- If your connection to the host drops, your game reconnects by itself and catches up.

## In a fight

- Your row is the normal battlefield. Each teammate has a panel along the top with their
  character, HP, block, energy, hand size, and the enemies in their row (you can target them).
- **The die**: rolled once per round, shown top left. Anyone may change it with Abacus,
  Toolbox, Gambling Chip or Gambler's Brew. Everyone presses **Keep N** (or plays a potion /
  ends the turn); then die relics trigger. Cards can't be played before that.
- Play cards whenever you like; plays from everyone resolve one at a time in order.
- "Any player" cards: drag the card onto a teammate's panel to give it to them (Block,
  Energy, Strength, card draw, removing Weak/Vulnerable). "All players" versions reach
  everyone automatically.
- Cards that let you switch rows (Dash, Swift Strike, Leap) ask who to switch with.
  Foreign Influence offers to copy another player's last Attack this turn. Block Potion and
  Oddly Smooth Stone ask who gets the Block; Tungsten Rod gives everyone 1 Block.
- **End Turn** marks you ready; enemies act when everyone is ready.
- **History** (top left): click a play to propose undoing back to before it; everyone
  must accept.

## Party decisions

- Neow: the host picks where the party starts (Act 1 or a Quick Start act).
- Dead Adventurer: one choice for the party, made by the host; one shared roll; the elite
  fight is for everyone. Lab: one party roll; the extra potion goes to one player.
- Big Fish: everyone must take a different option. Ancient Temple: each player who goes
  inside after another loses 1 more HP.

## Between fights

- Map: everyone clicks a room; most votes wins, ties are settled by a die roll.
- Rewards come from the enemy in your row; elite and boss rewards go to everyone.
- Left-side buttons outside combat: switch rows (both must ask), give a potion, pay gold
  toward a teammate's purchase (shop/event).
- Merchant stock is shared. Boss relics: one per player + 1; each relic once.
- If anyone dies, the party loses.

## Problems

Send the game log from every player: `sendToDevs/logs/SlayTheSpire.log` in the game folder.

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
- Host: **Host on port 7777**. The panel lists your addresses to share.
- Others: type or paste the host's address (`192.168.1.20:7777`) and **Join**.
  Same home network: use the host's local address. Over the internet: put everyone on
  Tailscale (or ZeroTier) and use the host's Tailscale address, or forward TCP 7777.
- Everyone picks a character. The host picks the ascension level and clicks **Start run**.

## In a fight

- Your row is the normal battlefield. Each teammate has a panel along the top with their
  character, HP, block, energy, hand size, and the enemies in their row (you can target them).
- **The die**: rolled once per round, shown top left. Anyone may change it with Abacus,
  Toolbox, Gambling Chip or Gambler's Brew. Everyone presses **Keep N** (or plays a potion /
  ends the turn); then die relics trigger. Cards can't be played before that.
- Play cards whenever you like; plays from everyone resolve one at a time in order.
- "Any player" cards: drag the card onto a teammate's panel to give it to them.
- **End Turn** marks you ready; enemies act when everyone is ready.
- **History** (top left): click a play to propose undoing back to before it; everyone
  must accept.

## Between fights

- Map: everyone clicks a room; most votes wins, ties are settled by a die roll.
- Rewards come from the enemy in your row; elite and boss rewards go to everyone.
- Left-side buttons outside combat: switch rows (both must ask), give a potion, pay gold
  toward a teammate's purchase (shop/event).
- Merchant stock is shared. Boss relics: one per player + 1; each relic once.
- If anyone dies, the party loses.

## Problems

Send the game log from every player: `sendToDevs/logs/SlayTheSpire.log` in the game folder.

# Event audit: StSBoardGameMod vs the board game

Read-only audit. Mod paths are relative to `src/main/java/BoardGame/` unless stated. Strings are in
`src/main/resources/BoardGameResources/localization/eng/DefaultMod-Event-Strings.json` (cited as "JSON").

## Reference sources used

- `sts-tbg/src/content/eventsContent.ts` (`ACT1_EVENT_DEFINITIONS`): Act I only. It has 17 unique cards plus a second Encounter!. Each card has a category: individual, exclusive draft, escalating, individual die, push-your-luck, party shared, or room conversion.
- `sts-tbg/src/engine/events.ts`: how each option resolves and the die tables (lines 1534-1935 and 2019-2200). It also has the die-modifier rules (lines 485-545 and 1941-2017):
  - Abacus +1 and Toolbox -1 wrap around (6 becomes 1, 1 becomes 6).
  - Gambling Chip rerolls.
  - Gambler's Brew sets the die to any value.
  - On a personal roll only the roller's items apply. On a party roll anyone's items apply.
- The physical card scans `sts-tbg/assets/events/sheets/Events_Act1.png` and `Events_Act2.png`, read at full resolution. These are the only source for Act II, because sts-tbg has no Act II data. **Act III events have no reference at all** (no data, no scan).
- Rulebook `~/Downloads/STS_KS_Rulebook.pdf`:
  - p8-9: "each player must choose one of those options ... To choose a Pay or Give option, you must first have it". A player "can use your gold to pay for any amount of another player's purchase at a Merchant or Event."
  - p19: once-per-room die items can be used at Events.
  - Component list: "51 Events 12/14/11 (6/5/2) (1 in unlock deck)".
  - Ascension 3 "Harder Events: add the [3] cards".
- `docs/MULTIPLAYER_RULES.md`.

Die notation below: "1-2 X" means die faces 1 and 2 give X.

## Deck composition (BoardGame.java:758-816)

| Act | Rulebook base / A3 | Mod base / A3 | Notes |
|---|---|---|---|
| I | 12 / 6 | 12 (LivingWall, Library, GoldenIdol, GoldenWing, Bonfire, Transmogrifier, Cleric, UpgradeShrine, Wheel, AccursedBlacksmith, Lab, BigFish) / 6 (ScrapOoze, WorldOfGoop, DeadAdventurer, Hallway x2, Merchant) | Counts match. The Act I scan shows the A3 flame only on Merchant, Dead Adventurer, World of Goop and Encounter x2. Scrap Ooze has no flame, but the 12/6 count needs it to be an A3 card. Probably a scan or print quirk (UNSURE). |
| II | 14 / 5 | 14 (Wheel, GoldShrine, CursedTome, BackToBasics, DrugDealer, Designer, WomanInBlue, Beggar, KnowingSkull, NoteForYourself, Colosseum, Nloth, WeMeetAgain, Joust) / 5 (Mausoleum, ForgottenAltar, Hallway x2, Merchant) | Counts match. The scan has no flame on Mausoleum or Forgotten Altar (UNSURE, same reasoning). |
| III | 11 / 2 | 12 (Wheel, Bonfire, Purification, MindBloom, TombRedMask, Falling, SecretPortal, WindingHalls, SensoryStone, NoteForYourself, MoaiHead, FaceTrader) / 3 (Hallway x2, Merchant) | One card too many in each group (UNSURE: no Act III reference). A Note for Yourself is in both Act II and Act III; the "1 in unlock deck" may explain one of them. |

The A3 gate is `Ascension3Condition` (BoardGame.java:752), which matches "Ascension 3: Harder Events". The flame badge on the scans reads "1", which is probably the print's badge style.

**No reference event is missing from the mod.** Name mapping:

| Board game name | Mod class |
|---|---|
| Wing Statue | BGGoldenWing |
| Ominous Forge | BGAccursedBlacksmith |
| Ancient Temple | BGGoldenIdolEvent |
| Wheel of Change | BGGremlinWheelGame |
| Augmenter | BGDrugDealer |
| Ancient Writing | BGBackToBasics |
| Encounter! | BGHallwayEncounter |
| The Merchant | BGMerchantEncounter |

Missing **options**:
- A Note for Yourself has no [Exchange] option.
- Face Trader's MP [Exchange] option exists in JSON but is commented out (events/BGFaceTrader.java:64). Act III has no reference, so this one is unverified.

## Cross-cutting multiplayer findings

1. **Gold pooling is not implemented.** The rulebook (p8) and MULTIPLAYER_RULES.md let any player pay part of another player's Pay cost at an Event. These Pay options check only your own gold: Cleric, Beggar, Woman in Blue, Designer, We Meet Again [Give Gold], Joust [Bet].
2. **The EventDie personal-roll display misses some events.** EventDieHooks only instruments `buttonEffect` of Joust, Mausoleum, AccursedBlacksmith, ScrapOoze (plus `takeReward`) and Dead Adventurer (multiplayer/ui/EventDieHooks.java:37-90).
   - Mausoleum and Forge roll inside `getReward(boolean,boolean)` (events/BGTheMausoleum.java:127,142; events/BGAccursedBlacksmith.java:197,213). Those calls are not instrumented, so their die never shows. The outcome rows for them in `EventDie.outcomes()` (lines 152-156) are never displayed.
   - Wheel of Change is not hooked at all, so the `offset` code for 0-5 rolls (EventDie.java:91) is dead.
3. **Event fights.**
   - Colosseum is treated as a per-player solo fight (multiplayer/combat/MPHooks.java:432-437), but the card says "One choice for the party" (see Colosseum below).
   - Mind Bloom [I am War] calls `MonsterHelper.getEncounter` directly (events/BGMindBloom.java:92). It is neither a party decision nor in `optionalEventFight()`, so a single player choosing it in MP is probably broken (UNSURE: no Act III reference, behaviour not traced).
4. **First-? discard rule.**
   - Dead Adventurer has it on the card, but the mod implements it as `floorNum==2 && BGExordium` (events/BGDeadAdventurer.java:85,111). That is only correct if floor 2 is always the first ? room. If the first ? comes later, it is not discarded (UNSURE).
   - The same floor-2 discard is also applied to Encounter! and The Merchant (events/BGHallwayEncounter.java:33, events/BGMerchantEncounter.java:42). Their cards do not carry that text (UNSURE whether a rulebook rule covers them).

## Act I events

### Living Wall (BGLivingWall)
- Card: [Forget] remove a card / [Change] transform / [Grow] upgrade. Mod: MATCH.
- No die. MP: each player chooses their own option, and the mod does this naturally (separate games). OK.

### The Library (BGTheLibrary)
- [Read]: choose 1 of 5 cards. MATCH: 5 cards, `getCard` at :93-101.
- [Rest]: heal 3. MATCH (:42).
- The JSON label says [Sleep] (cosmetic).
- No die. Per-player. OK.

### Bonfire Spirits (BGBonfire)
- [Offer]: remove a card. Uncommon heals 3, Rare heals to full: MATCH (:153-161).
- **Curse should lose 2 HP. The mod loses 1** (events/BGBonfire.java:125 `DamageInfo(null,1,HP_LOSS)`; JSON "[Prize?] Lose 1 HP"). MISMATCH, confirmed by both the card scan and the reference (eventsContent.ts:269).
- Per-player. OK.

### Transmogrifier (BGTransmogrifier)
- [Pray] transform 1; [Sacrifice] transform 2 and gain a Curse. MATCH (:100-116).
- Per-player. OK.

### Upgrade Shrine (BGUpgradeShrine)
- [Pray] upgrade 1: MATCH.
- **[Sacrifice] should lose 3 HP. The mod loses 2** (events/BGUpgradeShrine.java:85; JSON OPTIONS[1] also says 2). The card scan and the reference (eventsContent.ts:329) both say 3. MISMATCH.
- Per-player. OK.

### Lab (BGLab + SharedEvents + PartyEventUI + EventDie)
- Card: each player gains a potion. Roll once for the party: 4-6, one player gains another potion. Mod MATCH (4-6 at :62).
- Solo die items: Abacus turns 3 into a win, Toolbox turns 1 into 6, the Chip rerolls automatically, and the Brew is offered (:65-98). Correct.
- MP:
  - Party die through `EventDie.startParty` (PartyEventUI.java:259-267). Anyone may apply Abacus, Toolbox, Chip or Brew. Correct per the reference (events.ts:501-505).
  - The extra potion always goes to the row-0 player (SharedEvents.java:77-79). The card says "one player", and the reference lets the party vote (events.ts:2121-2134). This is a simplification, not wrong.
  - `outcomes()` text is correct.

### Big Fish (BGBigFish + SharedEvents.FishClaim)
- Banana: heal 2. MATCH.
- Donut: the mod upgrades the first upgradable starter Strike automatically, with no choice and no lock if none can be upgraded (:64-73). Minor.
- Box: relic and Curse. The Curse is not removed from the curse reward deck (:78-86), while other events call `removeCardFromRewardDeck`. Minor, UNSURE.
- Restraint: remove a Strike. MATCH.
- MP (each player takes a different option): claims go through a "fish" message.
  - **Bug:** `FishClaim.prefix` passes through when `buttonPressed > 2` (multiplayer/combat/SharedEvents.java:88). [Restraint] is option 3, so it is never claimed and several players can all take it. `onMessage` even has a name for it (:181).

### The Cleric (BGCleric)
- [Heal]: pay 1, heal 3. MATCH (:103-104).
- [Purify]: pay 3, remove a card. MATCH (:119).
- **[Prayer]: pay 2, upgrade a card. The mod never deducts the 2 gold** (events/BGCleric.java:108-114). There is no `loseGold` in that branch or in `update()`. MISMATCH, a free upgrade.
- The mod adds a [Leave] option. The reference offers Leave only when you cannot afford anything.
- MP: per-player. No gold pooling (see above).

### Wheel of Change (BGGremlinWheelGame)
- Card faces: 1 heal to full / 2 Curse / 3 remove a card / 4 relic / 5 gain 4 gold / 6 lose 2 HP. Each player rolls separately.
- The mod rolls `random(0,5)` and uses the wheel order 0 gold / 1 relic / 2 heal / 3 curse / 4 remove / 5 lose 2 HP (:239, :334-380). The amounts are right and the base odds are equal.
- **The Abacus and Toolbox shift along the wheel order, not the die faces** (:209-217, :275-290; the TODO at :35 admits it).
  - With Abacus, the card gives remove→relic, relic→gold, gold→lose HP, lose HP→heal.
  - With Abacus, the mod gives remove→lose HP, relic→heal, gold→relic, lose HP→gold.
  - The Toolbox is wrong the same way. MISMATCH.
- Chip and Brew: OK.
- MP: each player rolls separately in their own game, which is correct. The die is not shown (not hooked).

### Scrap Ooze (BGScrapOoze)
- Card: Reach Inside, roll, lose 1 HP. 1-2 Reach Inside again or Leave / 3-4 gain 2 gold / 5-6 gain a relic / [Leave].
- Mod: MATCH (:79-91, :216-244). Abacus/Toolbox wrap, Chip and Brew (gold or relic) are offered as buttons. Correct.
- JSON shows "66%". Damage is a normal `DamageInfo`, not HP loss; that makes no difference outside combat.
- MP: personal roll, shown by EventDie. OK.

### Wing Statue (BGGoldenWing)
- [Pray]: remove a card, lose 2 HP. MATCH.
- [Gather Gold]: gain 2. MATCH (:78-81).
- The JSON label is "[Destroy]" with unused "Requires card with X damage" strings (cosmetic).
- Per-player. OK.

### Ominous Forge (BGAccursedBlacksmith)
- [Rummage]: gain a relic, roll, 1-3 gain a Curse. MATCH (:199).
- [Forge]: upgrade a card, lose 2 HP. MATCH.
- Die items: Abacus (3), Toolbox (1→6) and the Chip apply automatically. The Brew is offered after a loss. Correct.
- The commented-out [Leave] (:58) is right, because the card has none.
- MP: per-player roll, but the die is never shown (see cross-cutting #2).

### Ancient Temple (BGGoldenIdolEvent + SharedEvents temple)
- [Go Inside]: gain a relic, lose 1 HP plus 1 per player who went in before you. [Leave].
- Mod: 1 + `templeExtra()` (:97-98), with the order taken from the "temple" message order. MATCH.
- The relic is always COMMON tier (:67), while other events use `returnRandomRelicTier()`. UNSURE.
- MP: the banner and option text show the extra cost (PartyEventUI.java:278-295). OK.

### The Merchant / Encounter! (BGMerchantEncounter, BGHallwayEncounter x2)
- Room conversions. A3-gated. See cross-cutting #4 for the floor-2 discard.

### Dead Adventurer (BGDeadAdventurer + EventDie + PartyEventUI)
- Card: one choice for the party. [Search]: 1-2 fight an Elite / 3-4 each player gains 2 gold / 5-6 **one player** gains a relic. [Leave].
- Mod ranges: MATCH (:366-382). Solo die buttons are correct (:233-284).
- MP: the host decides (`partySend`), and the party die goes through EventDie. OK.
- **On 5-6, `takeReward` runs in every game, so every player gets a relic** (events/BGDeadAdventurer.java:375-381). The EventDie table says the same ("Everyone obtains a Relic", multiplayer/ui/EventDie.java:146). Both are wrong against the card and the reference (events.ts:2167-2190: one relic, party assigns it).
- The 1-2 table text "(it drops 2 Gold and a Relic)" leaves out the card reward (minor).

### World of Goop (BGWorldOfGoop)
- Gather Gold: +3 gold, -2 HP. MATCH.
- Reach Deeper: relic and Curse. MATCH.
- Leave It: lose 1 gold, capped at what you have. MATCH.
- Per-player. OK.

## Act II events (source: Events_Act2.png)

### Knowing Skull (BGKnowingSkull)
- Card: choose one or two options. [Pick Me Up?] gain a potion, lose 1 HP / [Riches?] gain 2 gold, lose 1 HP / [Success?] gain a card, lose 2 HP.
- **The mod gives 3 gold for Riches** (events/BGKnowingSkull.java:192). Its first-screen label also says 3 (:102), but the second screen says 2 (:216).
- **[Success?] costs 1 HP in the mod** (`cardCost = 1`, :78), not 2.
- The `cost++` escalation (:168, :188, :198) never matters, because each option can be taken only once.
- The two-option cap matches. Per-player. MISMATCH x2.

### The Joust (BGTheJoust)
- Card: [Bet] pay 2 gold, a relic, or a potion. Roll: 4-6 gain 6 gold. [Leave].
- Mod: MATCH (:145, :181-218). Abacus 3, Toolbox 1→6, and the Chip apply automatically. The Brew turns a loss into a win.
- The JSON locked text says "Requires: 4 Gold" (OPTIONS[8]), but the gate is 2 (:115). Text bug.
- MP: personal roll, die shown. OK.

### Old Beggar (BGBeggar)
- Pay 2, remove a card / Leave. MATCH.

### The Woman in Blue (BGWomanInBlue)
- Buy 1: pay 2, 1 potion. Buy 2: pay 3, 2 potions. Leave: lose 1 HP.
- Mod: MATCH (:38-82). The JSON "Buy 3 Potions" string is unused.

### We Meet Again! (BGWeMeetAgain)
- Give Gold: pay 4, relic. Exchange: pay a relic, gain a relic. Give Card: remove a rare or uncommon card, gain a relic. MATCH.
- The mod adds an [Attack] option that does nothing (:81-82, :149-155). That lets a player skip a card with no Leave (minor).

### Designer In-Spire (BGDesigner)
- Adjustment: pay 2, upgrade. Clean Up: pay 3, remove. Full Service: pay 5, remove and upgrade. MATCH (:51-53, :188-215).
- The mod adds [Punch] (lose 1 HP) (:181, :224-229). It is not on the card (minor).

### N'loth (BGNloth)
- Offer a random relic (picked secretly, :55-68): choose a rare card. Offer a potion: a random rare card. Leave. MATCH.

### The Colosseum (BGColosseum)
- Card: "One choice for the party." [Warm Up] fight an Encounter / [Main Event] fight an Elite.
- **MP: there is no party handling.** Each player chooses in their own game, and MPHooks deliberately plays the fight solo (multiplayer/combat/MPHooks.java:432-437). PartyEventUI has no Colosseum banner. MISMATCH with the card.
- Rewards are cleared and replaced: 0 gold, plus a RARE relic for the Elite (:54-69). UNSURE against normal Encounter/Elite rewards.

### Augmenter (BGDrugDealer)
- [Drink Vial]: upgrade a card, lose 1 HP. MATCH.
- **[Become Test Subject]: the card says transform 2 and lose 3 HP. The mod loses 2** (events/BGDrugDealer.java:65; JSON OPTIONS[1] also says 2). MISMATCH.
- [Leave]: MATCH.

### A Note for Yourself (BGANoteForYourself)
- Card: [Exchange] give a player a card from your deck and they give you one of theirs / [Take] gain a card from another character's card reward deck.
- The mod has only [Take] (:56-85). **[Exchange], an MP-only option, is missing.**

### Ancient Writing (BGBackToBasics)
- [Elegance] remove a card; [Simplicity] upgrade a Strike and a Defend. MATCH. The Strike and Defend are picked automatically (first match).

### Forgotten Altar (BGForgottenAltar)
- Each player randomly picks and reveals one of their relics (:53-71). [Offer] lose it, gain a relic / [Sacrifice] lose 2 HP / [Desecrate] gain a Curse. MATCH.

### The Mausoleum (BGTheMausoleum)
- [Open Coffin]: gain a relic, roll, 1-3 gain a Curse. [Leave]. MATCH (:129).
- Die items are applied the same way as at the Forge. The die is not shown in MP (cross-cutting #2).

### Cursed Tome (BGCursedTome)
- [Take]: rare card reward and a Curse. [Read]: random rare card, lose 2 HP. [Skim]: card reward, lose 1 HP. MATCH (:107-137).

### Golden Shrine (BGGoldShrine)
- Pray: gain 2. Desecrate: gain 7 and a Curse. MATCH. The mod adds [Leave] (minor).

## Act III events (no reference: listed only)

Not verified against anything:
- Purifier: [Pray] remove a card / [Cleanse] remove all curses.
- Mind Bloom: War = fight an Act I boss for a rare relic / Rich = +5 gold / Healthy = full heal and a Curse / Awake = upgrade 2, lose 3 HP. See cross-cutting #3.
- Tomb of Red Mask.
- Falling.
- Secret Portal: gives a marker relic. Unclear how this works with the party's shared map position in MP (UNSURE).
- Winding Halls.
- Sensory Stone.
- Moai Head.
- Face Trader: only [Take and Give] is shown. [Exchange] is commented out (:64).
- Bonfire, Wheel of Change and Note for Yourself, as above.

## EventDie.outcomes() text check (multiplayer/ui/EventDie.java:130-164)

| Event | Rows | Verdict |
|---|---|---|
| Lab | 1-3 one potion each / 4-6 extra potion for the bottom player | Correct (the bottom-player choice is a simplification) |
| Dead Adventurer | 1-2 Elite / 3-4 2 gold each / 5-6 "Everyone obtains a Relic" | **5-6 is wrong**: one player gets the relic |
| Joust | 1-3 lose / 4-6 win 6 gold | Correct |
| Mausoleum / Forge | 1-3 relic and Curse / 4-6 relic | Correct text, but never shown (rolls not hooked) |
| Scrap Ooze | 1-2 / 3-4 / 5-6 | Correct |
| Wheel of Change | none | No table (and not hooked) |

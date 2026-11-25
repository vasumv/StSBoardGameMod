# Enemy audit: StSBoardGameMod vs sts-tbg reference

Read-only audit. Mod = `/home/vasu/fun/spire_board_game/StSBoardGameMod/src/main/java/BoardGame/` (paths below are relative to `monsters/` unless stated). Reference = `/home/vasu/fun/sts-tbg`.

## Reference sources used

- `src/content/enemyCardsData.ts` `ENEMY_CARD_SPECS` (74 cards: regular monsters, Act 1/2/3 elites, minions, some ascension variants). The `ascension` field is inconsistent (e.g. `chosen_a2_asc7` has `ascension: null`, `fungi_beast_a1_asc7` is labelled act 2 minion), so variant labels were taken from the ids.
- Bosses are **not** in `ENEMY_CARD_SPECS`. Boss data was transcribed by eye from the physical card scans in `assets/other/print_ready/Bosses.pdf`: a base card plus an "Ascension 10" card for each boss, and an "Ascension 11" card for the Heart. `src/content/sampleContent.ts` has a demo Guardian (40 HP per player, different moves); it was not used as a source.
- Act 1 and Act 3 elites were cross-checked against the scans `assets/enemies/sheets/Elites_Act1.png` and `Elites_Act3.png`. Each shows a base card and a "harder elite" variant. The variant badge is too small to read reliably; the reference labels it asc 8, and the Ascension list says Harder Elites are A1 and A12.
- `assets/other/print_ready/Acsention List.pdf`: 1 Harder Elites, 7 Harder Encounters, 10 Harder Bosses, 11 Harder Act IV, 12 Harder Elites.
- Icon readings: green crossed-sword = Weak, broken heart = Vulnerable, purple spiral = Dazed, green spiral = Slimed, flame = Burn, flexed arm = Strength. The red burst icon in front of an action means it hits ALL players (`{t:'aoe'}`).

## Boss reference data (transcribed from Bosses.pdf)

HP is listed for 1/2/3/4 players. "AOE" = the action hits all players. All boss damage and debuff rows carry the AOE icon.

| Boss | HP (base) | HP (A10) | Rows (base) | A10 differences | Abilities | Reward badge |
|---|---|---|---|---|---|---|
| Slime Boss | 22/44/66/88 | 23/46/68/92 | 1: AOE Slimed x3; 2: AOE 3 + Slimed x2; 3: AOE 6 | row1 Slimed x4 | Split: on death, at the start of the next round, summon a Large, an Acid and a Spike Slime per player; at A10 all Large Slimes then gain Str | 3 gold + boss relic + card (A10: 2 gold) |
| Hexaghost | 36/75/112/150 | 38/80/120/160 | 1: AOE 1 + Burn; 2: AOE 2x2 + Burn; 3: AOE Burn Burn; 4: AOE 3 + 5 Block; 5: AOE 2 + Burn; 6: AOE 3x3 + Burn Burn + Str | 1: 1 + Burn x2; 4: 4 + 5 Block; 5: 3 + Burn | (6 cube rows) | 3 gold (A10: 2) |
| The Guardian | 40/80/120/160 | 40/80/120/160 | Attack mode: 1: AOE 2 and 5 Block per player; 2: Mode Shift, AOE 6. Defensive mode: 1: AOE 2; 2: AOE 4 + Str, then enters Attack Mode at the start of the next round | Block 6 per player; Mode Shift 7; Defensive row 1 = 3 | Mode Shift: if it starts its turn with no Block, it does not attack and enters Defensive Mode next round. Sharp Hide (Defensive): after you play an Attack, take 1 damage | 3 gold (A10: 2) |
| The Collector | 57/114/171/228 | 60/120/180/240 | 1: Summon Torch Heads until there are 2 per player; 2: AOE 3, Str to all enemies; 3: AOE 5; grey row: AOE Weak Weak Dazed Dazed Burn Burn | row3 6; grey row Burn x3 | the grey row is the one-time row (inferred) | 3 gold (A10: 2) |
| The Champ | 40/80/120/160 | 45/90/135/180 | 1: AOE 4; 2: AOE Weak + Str; 3: AOE 5 + 3 Block. Fury Mode: grey: remove all Weak/Vuln tokens; AOE 4x4; Str | row3 6 + 3 Block; Fury Str x2 | Anger: at 0 HP heals 40 per player (A10 45), enters Fury Mode, move the cube now | 3 gold (A10: 2) |
| Bronze Automaton | 55/110/165/220 | 60/120/180/240 | 1: Str; 2: AOE 1x2; 3: Str + remove Weak/Vuln; 4: AOE 7 | row1 also removes Weak/Vuln; row4 9 | Start of combat: summon 1 Bronze Orb per player | 3 gold (A10: 2) |
| Awakened One | 50/100/150/200 | 50/100/150/200 | 1: AOE 3; 2: AOE 5; 3: AOE 2x2 | row2 6 | Start of combat: summon 2 Cultists per player. Curiosity: each hit deals extra damage equal to the number of your Powers. Awaken: on death, summon Phase 2 at the start of the next round | none shown |
| Awakened One P2 | 50/100/150/200 | same | 1: AOE 7; 2: AOE 4 + Slimed x2; 3: AOE 3x2 + Str | row1 reads 6 on the A10 card (as printed) | Void: when you draw Slimed, you must pay 1 Energy to Exhaust it if able. A10 Rebirth: when summoned, gains Str equal to the highest number of Powers any one player controls | |
| Donu | 50/100/150/200 | 55/110/165/220 | 1: Str to ALL enemies (not AOE); 2: AOE 3x2 | — | Start of combat: summon Deca | none shown |
| Deca | 50/100/150/200 | 55/110/165/220 | 1: AOE 3x2; 2: AOE Dazed + Slimed | row2 Slimed x2 | (has a 0-9 / 10-100 tracker) | |
| Time Eater | 60/120/180/240 | 64/128/192/256 | [clock 5] AOE 2x2; [clock 4] AOE Slimed x3; [clock 3] AOE 6 + Str | row1 3x2; row3 6 + Dazed + Str | Time Warp: each player can't play more cards this turn than the number on the clock. Haste: the first time it reaches 0 HP, heals 30 per player (A10 32), gains Str, removes Weak/Vuln | |
| Spire Spear | 42/84/126/168 | — | Each row: "Facing: Burn Burn" + (1: AOE 2x2; 2: AOE Dazed Dazed; 3: AOE 9) — scan is rotated, so the row/Facing pairing is uncertain | — | After start-of-turn effects, players can switch rows, then are immediately targeted by the Facing ability of the enemy in their row. Placed in the top 2 rows | |
| Spire Shield | 30/60/90/120 | — | rows (Facing / action): Lose 1 Energy / 20 Block; can't draw cards this turn / AOE 8; damage you deal this turn is reduced to 0 / Str x2 to all enemies | — | same Facing rule; placed in the bottom 2 rows | relic + card badge |
| Corrupt Heart | 100/200/300/400 | A11: 120/240/360/480 | grey: AOE Weak + Vuln + 5 Slimed shuffled into the draw pile; AOE 5; AOE 2x3; Str x2, Beat of Death +1 cube, loses Invincible | grey row uses 5 Burns; Beat of Death +2 cubes | Invincible: can't gain Weak/Vuln or go below 50 HP per player (A11 60). Beat of Death: at end of turn all players take 1 damage per cube; start of combat add a cube | |

## Act 1 (bgexordium)

Paths: mod classes are under `src/main/java/BoardGame/monsters/bgexordium/`; encounters are in `BoardGame.java` (addMonster, lines 528-584) and `dungeons/BGExordium.java`; rewards are in `monsters/MonsterGroupRewardsList.java` (MGRL). Die-controlled monsters read a 3-character behavior string: char0 = die faces 1-2, char1 = 3-4, char2 = 5-6. Sentry uses 2 characters: 1-3 and 4-6.
Reference: `enemyCardsData.ts` (ENEMY_CARD_SPECS), plus the Elites_Act1.png scan (A0 card and a flame-marked "harder" card; the ref JSON transcribes the harder card only for Sentries, as `asc=8`, probably a misread of A1 (inferred)) and Bosses.pdf (A0 and A10 cards).
How the mod and ref model encounters: a mod encounter is a group of monsters, while the ref uses one card per row and minion cards. Rewards are compared against the ref card of the encounter's primary monster.

### BGCultist (cultist_a1_card; also cultist_a1_pot, cultist_a2_monster, cultist_a2_minion, cultist_a3_minion)
- HP 9: MATCH (all 5 ref cards have 9).
- Action ATK1 + Str every turn: MATCH in effect. Turn 1 is move 3 (ATK1 + 1 Str). Later turns are move 1 (ATK1), and Ritual(1) adds 1 Str at end of each round (BGCultist.java:93,113-116). The Str timing differs slightly (end of round vs. after the attack) (inferred harmless).
- Rewards: `BGCultist` weak encounter = 1 gold (MGRL:30) vs cultist_a1_card `1c_card`: MATCH. Ref cultist_a1_pot (`1c_pot_card`) has no mod counterpart: NOT IN MOD. `3 Cultists` (act 2) = 2 gold vs cultist_a2_monster `2c_card`: MATCH.
- Ascension: the commented-out A7 HP and Ritual code is dead. No ref ascension variant.

### BGJawWorm (jaw_worm_a1; jaw_worm_a1_asc7; jaw_worm_a3_minion)
- difficulty 1 ("Jaw Worm (Medium)", BoardGame.java:566): HP 10 with DAs. 1-2 = ATK3 + 1 block, 3-4 = ATK4, 5-6 = 2 block + 1 Str. Matches jaw_worm_a1: MATCH.
- difficulty 3 (A7 "A7 Jaw Worm and Spike Slime"): HP 7 with dsa. ATK2 + 1 block / 2 block + Str / ATK3. Matches jaw_worm_a1_asc7: MATCH.
- difficulty 2 ("Jaw Worms (Hard)", act 3, BoardGame.java:660): HP 10, block 3, ATK4, thrash 3 + 1 block. Values match jaw_worm_a3_minion (DAS). The mod spawns 3 worms with rotated faces SDA/DAS/ASD, but ref has only the DAS card. Order for the other two: MISMATCH/uncertain (other summon cards may simply not be transcribed (inferred)).
- difficulty 0 ("Jaw Worm (Easy)" first encounter): HP 8, sda. NOT IN REFERENCE (first-encounter cards are not in ENEMY_CARD_SPECS).
- Rewards: Medium = 1 gold (MGRL:46) vs `1c_card`: MATCH. A7 = 1 gold (MGRL:48) vs asc7 `1c_card`: MATCH. Easy = 1 gold + potion: NOT IN REFERENCE. Hard (act 3) is a minion card with no reward.

### BGRedLouse (red_louse_a1_b, red_louse_a1_a)
- "2 Louse" RedLouse(false,"S21") is HP 3: Str / ATK2 / ATK1. Matches red_louse_a1_b: MATCH.
- "3 Louse (Hard)" RedLouse(true,"S22") is HP 4: Str / ATK2 / ATK2. Matches red_louse_a1_a: MATCH.
- The same encounter's third louse, RedLouse(false,"21S"), is HP 3: ATK2 / ATK1 / Str. NOT IN REFERENCE (BoardGame.java:558).
- Curl Up 2 block on first damage (BGCurlUpPower): MATCH.
- Rewards: "2 Louse" 1 gold (MGRL:32) and "3 Louse (Hard)" 1 gold (MGRL:39) vs `1c_card`: MATCH.

### BGGreenLouse (green_louse_a1_minion) / BGTestLouse
- HP 3: MATCH. Moves are ATK1, Weak 1, ATK2.
- Faces come from summon pool {1W2, 21W} (dungeons/BGExordium.java:91-92). Ref transcribes only 21W (ATK2 / ATK1 / Weak), which MATCHes. 1W2 is NOT IN REFERENCE (likely an untranscribed second summon card (inferred)).
- Curl Up 2: MATCH.
- BGTestLouse is an exact copy that no encounter uses (dead code).

### BGSpikeSlime_S (small_slime_a1)
- HP 3, ATK1: MATCH.
- Rewards: "Slime Trio" 1 gold (MGRL:38) vs `1c_card`: MATCH. "Easy Small Slimes" (first encounter) 0 gold + potion (MGRL:33): NOT IN REFERENCE.

### BGSpikeSlime_M (spike_slime_a1_minion)
- HP 5: MATCH. Moves: 1 = ATK1 + Dazed, 4 = ATK1 + Vuln 1, 2 = ATK2.
- Faces come from pool {2DV, V2D, DV2, VD2} (BGExordium.java:102-105). Ref transcribes only DV2, which MATCHes. The other three are NOT IN REFERENCE.
- Ref row 3-4 says "Acts last". NOT IN MOD: no acts-last logic exists anywhere in the mod.
- Slime Trio and "Cultist and SpikeSlime" use it as a regular monster. Ref has only the minion card for it.

### BGAcidSlime_M (acid_slime_a1_minion)
- HP 5: MATCH. Moves: 1 = ATK2 + Dazed (C), 2 = ATK2 (A), 4 = Weak 1 (L).
- Faces come from pool {CAL, LCA, ALC, LAC}. Ref transcribes only CAL, which MATCHes.

### BGAcidSlime_L (large_slime_a1, large_slime_a1_asc7, large_slime_a1_minion)
- **Encounter "Large Slime" (hard=false), A0:** MISMATCH. The mod uses a cube loop: move 6 is a 1-damage splash, move 7 is ATK4 + 1 Dazed, move 8 summons an Acid Slime M, then back to 6. HP is 8 (BGAcidSlime_L.java:81,92-93). Ref large_slime_a1 is a dice card: HP 6, 1-2 ATK3, 3-4 Weak Weak, 5-6 ATK2 + Dazed Dazed, with Split on death (summon Acid + Spike slime). The mod does not implement that card at all.
- **Same encounter, A7+:** HP 9, AOE ATK2, then ATK4 + 2 Dazed, then summon, looping from step 0. Matches large_slime_a1_asc7: MATCH.
  - Summon timing: the mod spawns immediately on its turn (BGAcidSlime_L.java:177-195). Ref says "at the start of next round": MISMATCH (minor).
  - AOE move is byte 6.
- **Slime Boss split (hard=true):** HP 12 with pool {4SW, SW4, W4S, W4S} (BGExordium.java:114-117). Moves: 4 = ATK1 + 2 Weak, 2 = ATK4, 1 = ATK3 + 2 Slimed.
  - Ref large_slime_a1_minion = W4S: MATCH. The other orders are NOT IN REFERENCE.
  - A10 +1 Str (line 74-76) matches the boss card A10 "Large Slimes gain Str": MATCH.
- Rewards: "Large Slime" 1 gold + potion (MGRL:40) vs `1c_pot_card` (both A0 and asc7): MATCH.

### BGFungiBeast (fungi_beast_a1; fungi_beast_a1_asc7)
- Encounter "Fungi Beasts" has two beasts, "21S" and "S21" (BoardGame.java:546-549). Faces: '2' = ATK2, '1' = ATK1 + 1 Str, 'S' = gain Str.
  - Faces 21S match fungi_beast_a1: MATCH.
  - HP is 5 (BGFungiBeast.java:60) vs ref 6: MISMATCH.
  - S gains 2 Str (BGFungiBeast.java:61) vs ref 1 Str: MISMATCH.
- A7 summon mode ("A7 Shelled Parasite and Fungi Beast", act 2, "s2!") is HP 6 and gains 1 Str: Str / ATK2 / ATK1 + Str. Matches fungi_beast_a1_asc7: MATCH.
- Spore Cloud (Vuln 1 on death, BGSporeCloudPower): MATCH.
- Rewards: "Fungi Beasts" 1 gold + potion (MGRL:37) vs `1c_pot_card`: MATCH.

### BGGremlinSneaky (sneaky_gremlin_a1, sneaky_gremlin_a1_minion)
- HP 2, ATK2: MATCH.
- As team leader it spawns 2 random gremlins from {Angry x2, Sneaky x2, Fat x2, Wizard x2} (BGExordium.java:125-150).
- Rewards: "Sneaky Gremlin Team" 1 gold + potion (MGRL:41) vs `1c_pot_card`: MATCH.

### BGGremlinAngry (mad_gremlin_a1, mad_gremlin_a1_variant, mad_gremlin_a1_minion)
- HP 4, ATK1, Angry(1) (+1 Str when hit by an attack): matches mad_gremlin_a1 and _variant: MATCH.
- As a spawned minion it still has HP 4 (BGGremlinAngry.java:47) vs ref minion card HP 5: MISMATCH.
- Rewards: "Angry Gremlin Team" 2 gold (MGRL:42) vs `2c_card`: MATCH.

### BGGremlinFat / BGGremlinWizard
- NOT IN REFERENCE (no Fat or Wizard gremlin card in ENEMY_CARD_SPECS).
- Mod Fat: HP 3, ATK1 (its weak debuff is commented out).
- Mod Wizard: HP 4, charges 1 turn, then ATK3 every turn.

### BGBlueSlaver (blue_slaver_a1; blue_slaver_a2_minion)
- HP 10: MATCH. Move bytes: 1 = ATK2 + Dazed ('d'), 2 = ATK3 + Dazed ('D'), 3 = ATK2 + Weak 1 ('W'), 4 = ATK2, 5 = ATK3.
- **Encounter "Blue Slaver"** uses "W3D" (BoardGame.java:563): 1-2 ATK2 + Weak, 3-4 ATK3, 5-6 ATK3 + Dazed. Ref blue_slaver_a1 has 5-6 = ATK2 + Dazed: **MISMATCH** (the 5-6 face should be 'd', not 'D').
- **Taskmaster minion (act 2)** uses default "W2d" (BGBlueSlaver.java:54): ATK2 + Weak / ATK2 / ATK2 + Dazed. Ref blue_slaver_a2_minion is ATK2 + Dazed / ATK2 + Weak / ATK3 ("dW3"): **MISMATCH**.
  - BGTheCity.getSummonBlueSlaver pool {Wd3, W3d, dW3, 3Wd} exists but nothing calls it.
- Rewards: "Blue Slaver" 2 gold (MGRL:43) vs `2c_card`: MATCH.

### BGRedSlaver (red_slaver_a1; red_slaver_a1_asc7; red_slaver_a2_minion; red_slaver_minion_variant)
- HP 10: MATCH. "DV3" = ATK2 + Dazed / ATK2 + Vuln 1 / ATK3. Matches red_slaver_a1 and red_slaver_a2_minion: MATCH.
- red_slaver_minion_variant (3DV) is NOT IN MOD. BGTheCity.getSummonRedSlaver pool has it, but nothing calls it.
- Ref asc7 "Acts last" on the Vuln face and the a2 minion footer "Red Slavers act last": NOT IN MOD. The mod also has no A7 variant.
- Rewards: "Red Slaver" 1 gold (MGRL:44) vs `1c_card`: MATCH.

### BGLooter (looter_a1; looter_a1_asc7; looter_a2)
- HP: A0 9 matches looter_a1, A7 7 matches looter_a1_asc7, hard 10 matches looter_a2: MATCH.
- Sequence: move 1 ATK2, then move 2 ATK3 + 1 block, then move 3 lose 2 gold and escape (no loop). Matches ref stages: MATCH. Rewards are still given when it escapes.
- Rewards:
  - "Looter" 0 gold + potion (MGRL:45) vs `pot_card`: MATCH.
  - "Looter (Hard)" 0 + potion vs looter_a2 `pot_card`: MATCH.
  - **"A7 Looter and Acid Slime" 0 + potion (MGRL:47) vs looter_a1_asc7 `2c_card`: MISMATCH.**

### BGGremlinNob (gremlin_nob_a1) — Elite
- HP: mod 14 at A0, 17 at A1-11, 19 at A12+ (BGGremlinNob.java:58-63).
  - Ref A0 card is 17/33/48/64. **MISMATCH: A0 solo is 14 vs 17.**
  - The harder (flame) card on the scan is 18/36/53/70. **MISMATCH: mod A1 is 17 vs 18.**
- Sequence: grey move 3 (Gains Enraged = BGAnger 1, once), then move 2 ATK3 repeated: MATCH. On the A1+ harder card the attack also gives +1 Str (mod adds it at A1+): MATCH.
- A12+: move 3 also deals 1 damage (BGGremlinNob.java:84-87). NOT IN REFERENCE.
- AOE: ref red row is AOE, so move 2 is AOE.
- Rewards: 2 gold + relic (MGRL:51) vs `2c_relic_card`: MATCH.

### BGLagavulin (lagavulin_a1) — Elite
- HP 22, both cards 22/44/66/88: MATCH at A0-11. A12+ HP is 24 (line 51): NOT IN REFERENCE.
- **A0:** asleep. Move 5 (Zzz) runs once, then move 3 ATK4, move 3 ATK4, move 1 (2 Weak + 1 Str), then loops 3,3,1 (lines 274-291). Matches ref stages with loop start 1: MATCH. Being hit while asleep gives move 4 (stunned) instead of 5, which has the same effect (inferred MATCH). Metallicize is commented out, which is consistent with ref.
- **A1-11:** no sleep. Move 7 (2 Weak), then 8 (ATK4), then 9 (ATK4 + Str), loop. Matches the harder scan card (AOE Weak Weak / AOE 4 / AOE 4 + Str): MATCH.
- **A12+:** order 10 (Weak), then 11 (ATK4 + Str), then 12 (ATK4). NOT IN REFERENCE.
- AOE moves: 1, 3, 7, 8, 9 (and by analogy 10, 11, 12).
- Rewards: 2 gold + relic vs `2c_relic_card`: MATCH.

### BGSentry (sentries_a1; sentries_a1_asc8 = harder card; sentry_a_a1_minion) — Elite
- Encounter "3 Sentries": BGSentry "3D", "D3", "2D".
  - "2D" is the Sentries card. A0: 1-3 ATK2, 4-6 Dazed: MATCH. **HP: mod 7 at A0 (BGSentry.java:53-54) vs ref 8: MISMATCH.**
  - "D3" (HP 7, Dazed / ATK3) matches sentry_a_a1_minion: MATCH.
  - "3D" (HP 8, ATK3 / Dazed) is presumably Sentry B. NOT IN REFERENCE.
- **Harder card (A1+, "2D"):** grey Dazed once, then ATK2, then Dazed x2, looping from step 1. Mod moves 6 → 7 → 8 → 7 … MATCH.
  - **HP: mod 8 at A1-11 (line 58) vs ref 9: MISMATCH.** Mod A12 is 9.
- Ref card summons Sentry A/B alternating "until there are 3 enemies per player". The mod uses a fixed group of 3 (fine for solo; MP needs 3 per player).
- No AOE.
- Rewards: 2 gold + relic (MGRL:52) vs `2c_relic_card`: MATCH.

### BGSlimeBoss (boss: Slime Boss; Bosses.pdf) — Boss
- HP: 22 (A10: 23) vs card 22/44/66/88 (A10: 23/46/68/92): MATCH.
- Sequence: move 1 = Slimed x3 (A10: x4), move 2 = ATK3 + 2 Slimed, move 3 = ATK6, then loop. Matches the card rows: MATCH. All three rows are AOE.
- Split: at 0 HP it switches to move 4, spawning 1 Large (hard), 1 Acid M and 1 Spike M (BGSlimeBoss.java:144-146).
  - Card summons one set per player "at start of next round". The mod spawns on its next monster turn (minor timing difference (inferred)). MP needs one set per player.
- Rewards: 3 gold (MGRL:56), and A10 reduces boss gold by 1 to 2 (AbstractBGDungeon goldModifier). Card shows 3 gold (A10: 2) + boss relic + card: MATCH.

### BGHexaghost (boss: Hexaghost) — Boss
- HP: 36 (A10: 38) vs card 36/75/112/150 (A10: 38/80/120/160): MATCH.
- The 6 moves run in order and loop (orbActiveCount): MATCH.

| Move | Mod (A10 in parentheses) | Card (A10 in parentheses) | Result |
|---|---|---|---|
| 1 | 1 dmg + 1 Burn (2 Burns) | 1 + Burn (+2 Burns) | MATCH |
| 2 | 2x2 + 1 Burn | 2x2 + 1 Burn | MATCH |
| 3 | 2 Burns | 2 Burns | MATCH |
| 4 | 3 (4) dmg + 5 block | 3 (4) dmg + 5 block | MATCH |
| 5 | 2 (3) dmg + 1 Burn | 2 (3) dmg + 1 Burn | MATCH |
| 6 | 3x2 + 2 Burns + 1 Str | 3x3 + 2 Burns + Str | MATCH |

- All six rows are AOE. Burns go to the discard pile.
- Rewards: 3 gold (A10: 2): MATCH.

### BGTheGuardian (boss: The Guardian) — Boss
- HP 40 at both A0 and A10, card 40/80/120/160: MATCH.
- **Attack mode:** move 5 = 2 dmg + 5 block (A10: 6), then move 2 = Fierce Bash 6 (A10: 7), then back to 5. Matches the card (AOE 2 with 5/6 block per player; Mode Shift AOE 6/7): MATCH.
  - The mod's block amount is the solo value. The card gives that much block per player.
- **Mode Shift:**
  - Mod: when the player breaks the Guardian's block in attack mode (publicBrokeBlock, lines 321-339), it switches immediately to defensive and its next move becomes move 1 (Close Up: no attack, applies Sharp Hide 1).
  - Card: "if it starts its turn with no block, it doesn't attack and enters Defensive Mode at start of next round."
  - Effectively equivalent, because block persists until its next turn (inferred MATCH). The trigger point differs (on block break vs. start-of-turn check).
- **Defensive:** move 3 = Roll 2 (A10: 3), then move 4 = Twin Slam 4 + 1 Str, remove Sharp Hide, back to Offensive. Matches the card (AOE 2/3; AOE 4 + Str, enters Attack Mode): MATCH.
- Sharp Hide (take 1 after playing an Attack): MATCH.
- AOE moves: 2, 3, 4, 5.
- Rewards: 3 gold (A10: 2): MATCH.

### Ascension summary (Act 1)
- A1 elites:
  - Nob gains Str on attack: MATCH. Nob HP 17 vs harder card 18: MISMATCH.
  - Lagavulin starts awake: MATCH.
  - Sentries cube pattern: MATCH. Sentries HP 8 vs 9: MISMATCH.
- A7 encounters:
  - Large Slime asc7 card: MATCH.
  - Jaw Worm asc7: MATCH.
  - Looter HP 7: MATCH. Looter A7 reward: MISMATCH.
  - Red Slaver asc7 "acts last": NOT IN MOD.
- A10 bosses: Slime Boss, Hexaghost and Guardian values all MATCH Bosses.pdf. Boss gold is -1: MATCH.
- A12 elites: Nob HP 19 + first-turn 1 dmg, Lagavulin HP 24 + reordered cube, Sentry HP 9. These are NOT IN REFERENCE: the scan shows only one harder variant per elite.

## Act 2 (bgcity)

Paths are relative to `StSBoardGameMod/src/main/java/BoardGame/`. Encounter definitions are in `BoardGame.java` (around lines 586-660) and rewards in `monsters/MonsterGroupRewardsList.java:58-79`.

**How Act 2 elite rewards work:** `MonsterGroupRewards.upgrade` is never set or read. Act 2+ elite cards are upgraded by `dungeons/AbstractBGDungeon.java:342-345`, which upgrades card rewards in any elite room outside Exordium. So Act 2 "2c_relic_upgrade" is a MATCH. The same code also upgrades **Act 3** elite cards, but the Act 3 reference says `2c_relic_card` (no upgrade). That case belongs to the Act 3 audit and is noted here only.

**Classes from other acts used in Act 2 encounters:** BGCultist, BGFungiBeast (A7), BGSentry (A7), BGLooter, BGBlueSlaver/BGRedSlaver (Taskmaster minions), and the gremlins. The Act 1 section covers them.

### BGBookOfStabbing (book_of_stabbing_a2, reference card flagged asc=1)
- HP: mod A0 30 / A1-11 35 / A12 36 (`bgcity/BGBookOfStabbing.java:57-63`). Reference 35/70/105/140 is the A1 card. MATCH at A1. There is no A0 reference card, so mod A0 30 is NOT IN REFERENCE.
- Rows: the reference loops from step 0 over two rows: [AOE 3 + Str] and [AOE 1,1 + Str].
  - Mod move 2 = 3 dmg + Str 1 (`:113-119`). Move 1 = 2×1 dmg + Str 1 (`:88-108`). The moves alternate 2↔1. MATCH.
  - Opening move: A1+ opens with move 2 (`:73-74`), which MATCHes the reference. A0 opens with move 1 (`:76`), which is NOT IN REFERENCE.
- Painful Stabs: adds 1 Dazed when the Book deals unblocked damage (`powers/BGPainfulStabsPower`). The power is removed before the first hit of the 2-hit move and re-added after it (`:89,:99`), so the move gives at most one Dazed. MATCH (inferred from the code order).
- Rewards: `Book of Stabbing` (2 gold, relic, upgrade via AbstractBGDungeon) vs reference 2c_relic_upgrade. MATCH.
- Ascension variants:
  - A12: HP 36 and big stab 4 (`:58,:66`). NOT IN REFERENCE.

### BGTaskmaster (taskmaster_a2, reference card flagged asc=1)
- HP: reference A1 card is 15/32/48/64. Mod (`bgcity/BGTaskmaster.java:43-49`) is 13 at A0, 15 at A2-11 and 16 at A12.
  - **MISMATCH at A1:** the condition is `ascensionLevel>1` (`:45`), so A1 gets 13 HP instead of 15.
- Rows: reference is grey [AOE 1] once, then red [AOE 2 + Daze + Str] repeating (loop 1).
  - Mod A1-11 opens with move 1 = 1 dmg (`:60-62,:82-88`). Move 2 = 2 dmg + 1 Dazed + Str 1 and repeats (`:89-97`). MATCH.
- A0: move 0 = 1 dmg + Str 1, repeating, never dazes (`:64,:74-81`). NOT IN REFERENCE.
- A12: move 3 = 1 dmg + 1 Dazed + Str 1, repeating (`:60,:98-106`). NOT IN REFERENCE.
- Encounter: Blue Slaver + Taskmaster + Red Slaver (`BoardGame.java:649-652`). The slavers use the reference's Act 2 minion slaver cards, which the Act 1 section covers.
- Rewards: `Taskmaster` (2 gold, relic, upgrade) vs reference 2c_relic_upgrade. MATCH.

### BGGremlinLeader (gremlin_leader_a2)
- HP: reference 30/60/90/120. Mod A0 30 MATCH. Mod A1-11 35 and A12 40 (`bgcity/BGGremlinLeader.java:80-85`) are NOT IN REFERENCE.
- Rows: the reference loops from step 0 over three rows:
  - Row 1 [AOE 2, then Block 1 + Str 1 to ALL enemies]: mod move 1 deals 2 dmg (A1+: 3, `:94-97`) and gives Str 1 + Block 1 to every living monster including the Leader (`:114-126`). MATCH at A0. The A1+ damage of 3 is NOT IN REFERENCE.
  - Row 2 [AOE 5]: mod move 2 deals 5 (`:131-136`). MATCH.
  - Row 3 [Revive all dead Gremlins]: mod move 3 re-spawns the dead gremlin slots with their original types (`:139-145`, `actions/BGSpawnTwoGremlinsForGremlinLeaderAction`). MATCH.
  - Cycle 1→2→3→1. MATCH.
- Summons: at combat start the mod spawns 2 gremlins drawn from a pool of {Angry, Sneaky, Fat, Wizard}×2 (`:103`, `dungeons/BGExordium.java:126-151`) and gives Angry gremlins Anger. The reference card states no count, so this is NOT IN REFERENCE. For multiplayer, the gremlin count per player still has to be decided; the physical game likely scales per player (inferred).
- A12: the revive also gives Str 1 to all enemies (`:142-144`). NOT IN REFERENCE.
- Rewards: `Gremlin Leader` (2 gold, relic, upgrade) vs reference 2c_relic_upgrade. MATCH.

### BGGremlinPlaceholder (no reference card)
- A 0-HP dead slot filler used only by the Gremlin Leader encounter (`BoardGame.java:643-647`). NOT IN REFERENCE because it is a mod mechanism, not an enemy.

### BGChosen (chosen_a2 at 16 HP; chosen_a2_asc7 at 14 HP)
- One class, with HP passed into the constructor:
  - `Chosen and Cultist` uses 14 HP (`BoardGame.java:604`).
  - `Chosen and Byrd` uses 16 HP (`BoardGame.java:608`).
- Mod actions are the same in both encounters (`bgcity/BGChosen.java:69-71,87-112,134`):
  - Move 1 (once): 1 dmg + 1 Dazed.
  - Then moves 2 and 3 alternate: move 2 = 3 dmg + 2 Dazed; move 3 = 5 dmg + Str 1.
- Against the 14-HP card (grey [AOE 1 + Daze], then [3 + Daze Daze] and [5 + Str], loop 1): MATCH.
- Against the 16-HP card (chosen_a2: red [AOE Daze] with no damage, [2 + Daze Daze], [4 + Str], loop 0, so the AOE Daze repeats): **MISMATCH** in all three rows and in the loop (`BGChosen.java:69-71,92-112`).
- Rewards:
  - `Chosen and Cultist` (1 gold, potion) vs 14-HP card 1c_pot_card. MATCH.
  - `Chosen and Byrd` (2 gold) vs chosen_a2 2c_card. MATCH.
- Note: the reference card id says "asc7" but its ascension field is null. Treat it as a normal-encounter variant (inferred).

### BGSnakePlant (snake_plant_a2)
- HP 17 (`bgcity/BGSnakePlant.java:73`). MATCH.
- Die faces "MMW" (`:65,:131-145`):
  - Faces 1-2 and 3-4: move 1 hits 3 then 2 (`:77-78,:108-118`). MATCH.
  - Faces 5-6: move 2 applies Weak 2 (`:122`). MATCH.
- Ascension variants:
  - A7 card (snake_plant_a2_asc7): the 2-dmg hit is AOE. NOT IN MOD; the mod has no A7 branch.

### BGSnecko (snecko_a2)
- HP 23 (`bgcity/BGSnecko.java:71`). MATCH.
- Die faces:
  - 1-2: 3 dmg + Confused 2. MATCH.
  - 3-4: **5 dmg** + Confused 1. The reference says 4. **MISMATCH** at `BGSnecko.java:75` (`damage.add(...,5)`), used at `:175`.
  - 5-6: 2 dmg + Confused 3. MATCH.
- Confused passive: `BGConfusionPower` (starts at -1, `:84`) blocks the first card each turn if energy is below the value. That roughly matches "first card costs the Confused value" (inferred).
  - The reference's "only if Snecko is in your row" condition matters for multiplayer and has no mod equivalent.
- Rewards: `Snecko` (1 gold) vs 1c_card. MATCH.

### BGSphericGuardian (spheric_guardian_a2; also the a2_asc7 and a3_asc11 cards)
- HP 5 (`bgcity/BGSphericGuardian.java:46`). MATCH.
- Start of combat: Barricade + 10 block (`:64-67`). MATCH.
- Cube rows, loop 0: move 1 = 2 dmg + 5 block; move 2 = 5 dmg; alternating (`:73-85,:118`). MATCH.
- All three reference cards are identical. The mod also uses this class in `A7 Spheric Guardian and Sentry A` and in the Act 3 `A7 Repulsor and Friends`.
- Rewards:
  - `SphericGuardian` (1 gold) vs 1c_card. MATCH.
  - `A7 Spheric Guardian and Sentry A` (1 gold) vs a2_asc7 1c_card. MATCH.

### BGByrd (byrd_a2_monster; byrd_a2_minion)
- HP: default 4 (`bgcity/BGByrd.java:61`). MATCH.
  - In `3 Byrds` the front Byrd gets **HP 5** (`BoardGame.java:625`) while the reference says 4. **MISMATCH**.
- Moves: move 1 = 1×2 pecks; move 2 = 3 dmg; move 3 = Str 1 (`:89-107,:163-168`). Values MATCH.
- Die orders:
  - Front Byrd "1S3" (`BoardGame.java:625`) MATCHes the monster card (1-2 peck, 3-4 Str, 5-6 swoop).
  - Summoned Byrds draw from the pool {S13, S31, 31S} (`dungeons/BGTheCity.java:82-91`). Only S31 is in the reference minion card; S13 and 31S are NOT IN REFERENCE.
- Flying: `BGFlightPower` caps each hit at 1 damage. MATCH.
  - The grounded/stun states (`:129-139`) are never triggered. They look like dead code (inferred).
- Code note: move 2 `return`s without `RollMoveAction` (`:102`). This is harmless because the die sets the move.
- Rewards: `3 Byrds` (1 gold) vs 1c_card. MATCH.

### BGShelledParasite (shelled_parasite_a2)
- HP: 18 below A7, 16 at A7+ (`bgcity/BGShelledParasite.java:70-73`). 18 MATCHes; the A7 HP of 16 is NOT IN REFERENCE.
- Moves (`:92-128,:165`) against reference loop 1: grey [4 + 2 block], then red [3 + Vuln + 2 block] ↔ [2 + 2 block].
  - Move 1 = 4 dmg + 2 block, once.
  - Move 2 = 3 dmg + Vulnerable 1 + 2 block.
  - Move 3 = 2 dmg + 2 block.
  - Moves 2↔3 alternate. MATCH.
- Extra mod behavior: in the A7 encounter with Fungi Beast, move 2 also gives the Fungi Beast a Vulnerable-watch power (`:108-112`). NOT IN REFERENCE.
- Rewards:
  - `Shelled Parasite` (potion) vs pot_card. MATCH.
  - `A7 Shelled Parasite and Fungi Beast` (potion). There is no A7 parasite card in the reference.

### BGCenturion (centurion_a2 = "3B"; centurion_a2_asc7 = "B3")
- HP 15 (`bgcity/BGCenturion.java:49`). MATCH.
- Faces 1-3 / 4-6 come from the behavior string (`:123-141`):
  - Attack = 3 dmg.
  - Block = 3 to `monsters.get(1)` (the Mystic, by index) (`:80`).
  - MATCH for both cards. `Centurion A` uses "B3" (`BoardGame.java:588`); `Centurion B` uses "3B" (`:592`).
- Fury: when the Healer dies, the Centurion gets Str 1 and attacks every turn from then on (`:146-152,:126-127`). MATCH.
- Rewards:
  - `Centurion A` (1 gold) vs asc7 card 1c_card. MATCH.
  - `Centurion B` (1 gold, potion) vs 1c_pot_card. MATCH.

### BGHealer = Mystic (mystic_a2_minion at 10 HP; mystic_minion_variant at 12 HP)
- HP is always 12 (`bgcity/BGHealer.java:49`).
- Behavior is drawn from {H2S, 2SH} (`dungeons/BGTheCity.java:71-79`):
  - "2SH" matches the 12-HP variant card. MATCH.
  - "H2S" matches the 10-HP card, but the mod gives it 12 HP. **MISMATCH**.
- Values: attack 2 + Weak 1, heal 3 to all enemies, Str 1 to all enemies (`:70-99`). MATCH.
- The variant card says "Acts last" on its Str row. The mod has no act-last ordering: NOT IN MOD.
- Death: triggers the Centurion's Fury (`:172-176`). MATCH.
- Reward: minion, none. MATCH.

### BGMugger (mugger_minion_variant at 12 HP; mugger_a2_minion at 10 HP)
- HP 12 (`bgcity/BGMugger.java:56`).
- Sequence: move 1 = 2 dmg + 1 block → move 2 = 2 dmg → move 3 = 4 dmg + 2 block → move 4 = lose 2 gold and escape (`:80-138`, escapeDef=2 at `:42`). This MATCHes the 12-HP variant card.
- The 10-HP card (first step is ATK 2 with no block) is NOT IN MOD.
- Thievery power and the fake gold-steal display are cosmetic.
- Encounters: `Looter (Hard)` and `Another Looter (Hard)` (`BoardGame.java:595-602`), both rewarding a potion. That MATCHes looter_a2 pot_card; the Looter is the front enemy.

### BGBronzeAutomaton (boss: Bronze Automaton)
- HP: 55 below A10, 60 at A10+ (`bgcity/BGBronzeAutomaton.java:64`). MATCH against 55/110/165/220 and A10 60/120/180/240.
- Rows, cycling 1→2→3→4 (`:95-122,:130`):
  - Move 1 = Str 1; at A10 it also removes Weak/Vuln. MATCH.
  - Move 2 = AOE 1+1. MATCH.
  - Move 3 = Str 1 + remove Weak/Vuln. MATCH.
  - Move 4 = AOE 7 (A10: 9) (`:67`). MATCH.
- Start of combat: spawns 1 Bronze Orb (`:89`). The reference says one per player; MATCH for solo.
- Rewards: `Automaton` (3 gold, minus 1 at A10 via `AbstractBGDungeon` addGoldToRewards) vs the card badge (3, A10: 2). MATCH.

### BGBronzeOrb (bronze_orb_a2_a)
- HP 19 (`bgcity/BGBronzeOrb.java:54`). MATCH.
- Values (`:63-84`) MATCH the reference:
  - "3" = 3 dmg.
  - "D" = 2 dmg + 1 Dazed.
  - "B" = 3 block to the Automaton.
- Die orders are drawn from {3DB, 3BD, DB3, B3D} (`dungeons/BGTheCity.java:120-130`). Only 3DB (Orb A) is in the reference; the other three are NOT IN REFERENCE. The physical "Bronze Orb B" card was probably not transcribed (inferred).

### BGTorchHead (torch_head_a2)
- HP 9 and attack 1 every turn (`bgcity/BGTorchHead.java:41-42,52`). MATCH.

### BGTheCollector (boss: The Collector)
- HP: 57 below A10, 60 at A10+ (`bgcity/BGTheCollector.java:78`). MATCH against 57/114/171/228 and A10 60/120/180/240.
- Rows (`:113-164`):
  - Move 1: summon Torch Heads until there are 2 (re-summons dead ones). The reference says 2 per player; MATCH for solo.
  - Move 2: 3 dmg + Str 1 to all monsters. MATCH.
  - Move 3: 5 dmg (A10: 6) (`:86`). MATCH.
  - Move 4 (once): Weak 2, 2 Dazed into the draw pile, and 2 Burns (A10: 3) into the discard pile (`:160-162`). The reference's grey row reads as [AOE Weak Weak, Daze Daze, Burn Burn] (A10: 3 Burns). MATCH. The icons were read from the Bosses.pdf scan.
- Sequence: 1→2→3→4 for the first cycle, then 1→2→3→1. Using the grey row once is consistent with how grey cubes work (inferred).
- Rewards: `Collector` (3 gold, minus 1 at A10). MATCH.

### BGChamp (boss: The Champ)
- HP: 40 below A10, 45 at A10+ (`bgcity/BGChamp.java:77`). MATCH against 40/80/120/160 and A10 45/90/135/180.
- Normal cycle 1→2→3 (`:113-138`):
  - Move 1 = 4 dmg. MATCH.
  - Move 2 = Weak 1 to the player + Str 1. MATCH.
  - Move 3 = 5 dmg (A10: 6) + 3 block. MATCH. The `blockAmt` field is uninitialized and contributes 0.
- Anger: `cannotLose` is set at combat start. At 0 HP the Champ heals `maxHealth` (40/45) and switches to move 4 (`:297-309`). MATCH for solo; the reference heals 40 (A10: 45) per player.
- Fury: move 4 removes Weak/Vuln (grey row) → move 5 = 4+4 → move 6 = Str 1 (A10: 2) (`:79`) → back to 5. MATCH.
- Rewards: `Champ` (3 gold, minus 1 at A10). MATCH.

## Act 3 (bgbeyond) and Act 4 (bgending)

Paths are relative to `StSBoardGameMod/src/main/java/BoardGame/`. "Ref A1 side" means the harder-elite face of the Elites_Act3 scan. Its ascension badge was hard to read; it is treated as the A1 "Harder Elites" side (inferred). Boss data was read from `assets/other/print_ready/Bosses.pdf`. The `🔥10`/`🔥11` badges there are the A10/A11 sides.

### BGGiantHead (giant_head_a3)
- HP solo: 80. MATCH (BGGiantHead.java:72). Ref HP scales 80/160/240/320.
- Sequence: the mod plays move 0 three times ("3", "2", then quote), then move 2 (ATK 7 + 1 Str) every turn after. Ref has 3 grey rows, then a red AOE ATK7 + Str row that loops. MATCH (getMove :189-205). Moves 0, 1 and 3 do nothing besides the shout.
- Slow: MISMATCH. Ref: for each player, Giant Head gains Vuln after that player plays their 4th card each turn. Mod: BGSlowPower gives a flat +1 to NORMAL damage taken, always on (powers/BGSlowPower.java:28-33). The 4th-card logic is commented out.
- Rewards: "BoardGame:Giant Head" gives 3 gold + relic (MonsterGroupRewardsList.java:91). Ref is 2c_relic_card and the scan coin shows 2. MISMATCH (gold 3 vs 2).
- Ascension variants:
  - A1: HP 85. MATCH (:75).
  - A1 row 1 is "AOE Weak". Mod has it as dead code `if(false)` at :113. MISMATCH.
  - A1 row 2 is "AOE 3". Mod uses move 4 with damage secondaryDmg=5 (:76, :201). MISMATCH (5 vs 3).
  - A1 final attack is 8 + Str. MATCH (:77).
  - A12 in mod: HP 90, row-2 damage 6, final 9. NOT IN REFERENCE.

### BGNemesis (nemesis_a3)
- HP solo: 30. MATCH (BGNemesis.java:86).
- A0 values: MISMATCH (:87-90).
  - Ref: 5 Burn / ATK5 / ATK2x3 / ATK8.
  - Mod: 4 Burn / ATK4 / ATK2x2 plus 1 Burn / ATK7.
- Cycle: MISMATCH. The ref card has 4 red rows and loopStart=0, so it returns to the Burn row. The mod goes 0→1→2→3, then loops 2↔3 forever (:144 sets the next move to 2). The Burn row and the 5-damage row never repeat.
- Intangible: MISMATCH (inferred). Ref: Nemesis can't lose HP only while the cube is on the 5 or 8 row. Mod applies BGIntangiblePower after every turn, whatever the move (:151-153). The guard `hasPower("Intangible")` never matches the power ID "BGIntangible". The power removes itself at the next end of turn.
- Rewards: 3 gold + relic (MonsterGroupRewardsList.java:92). Ref is 2c_relic_card. MISMATCH (gold).
- Ascension variants:
  - A1 HP 35. MATCH (:92).
  - A1 ref: 5 Burn / 5 / 2x3 plus 2 Burn / 9.
  - A1 mod: 5 / 5 / 2x2 plus 1 Burn / 8. MISMATCH on the multi-hit count, the secondary burns and the last hit (:93-96).
  - A12 in mod: HP 36, 5 / 6 / 2x3 plus 2 Burn / 8. NOT IN REFERENCE.

### BGReptomancer (reptomancer_a3)
- HP solo: 35. MATCH (BGReptomancer.java:63).
- Summon: 2 Daggers per summon (:57). This equals "2 per player" for solo only.
- A0 sequence: summon (0) → 3x2 (1) → ATK7 (2) → loop to 0. Rally: if no Daggers are alive, it skips to 0. Sequence and Rally MATCH (:146-157).
- A0 bottom row: MISMATCH. Ref is ATK7 + Weak. Mod's Weak is commented out (:172).
- Rewards: 3 gold + relic (MonsterGroupRewardsList.java:93). Ref is 2c_relic_card. MISMATCH (gold).
- Ascension variants:
  - A1 HP 40. MATCH (:65).
  - A1 ref rows: summon / AOE 7 + Weak + Rally / AOE 3,3 + Str.
  - A1 mod: move 3 is 7 + Daze x1 with Weak commented out (:186-189). MISMATCH (Daze instead of Weak).
  - A1 mod: move 4 is 4x2 + Str (:74, :212-219). MISMATCH (4x2 vs 3x2).
  - A12 in mod: HP 42, Daze x2. NOT IN REFERENCE.

### BGSnakeDagger (dagger_a3_minion)
- HP 5, ATK2, then ATK5 and dies (LoseHP self). MATCH (BGSnakeDagger.java:33-36, :63-65, :83-90).

### BGDarkling (darkling_a3)
- HP 8. MATCH (BGDarkling.java:80).
- Faces:
  - 'S' = 3 Block + 1 Str.
  - '2' = 2x2.
  - '3' = ATK3 + heal 2.
- The ref card is [1,2] S / [3,4] ATK3 heal 2 / [5,6] 2x2, which is behavior "S32". MATCH for the third Darkling.
- The "3 Darklings" encounter also uses "2S3" and "32S" (BoardGame.java:704-708). Those face orders are NOT IN REFERENCE (only 1 Darkling card).
- Regrow: dead Darklings come back with 4 HP, run from the MonsterGroup.applyEndOfTurnPowers postfix (:250-262). Ref says "start of turn". MATCH (inferred, same timing in practice).
- Combat ends when all Darklings are half-dead. MATCH.
- Rewards: "3 Darklings" gives 0 gold + potion (MonsterGroupRewardsList.java:90). The ref Darkling card is a minion card with no reward. NOT IN REFERENCE.

### BGExploder (exploder_a3_minion / exploder_a3_monster)
- HP 8. MATCH.
- Sequence: ATK3 → nothing ("...") → ATK10 and dies (SuicideAction). MATCH (BGExploder.java:88-100, :113-119).
- Rewards: "Exploder and Friends" and "A7 Exploder and Friends" give 1 gold (MonsterGroupRewardsList.java:83, :94). Ref exploder_a3_monster is 1c_card. MATCH.

### BGRepulsor (repulsor_a3_minion)
- HP 7.
- Faces: 'D' = ATK1 + 2 Daze; '3' = ATK3. Values MATCH (BGRepulsor.java:66-83).
- Ref faces [1-3] ATK3 / [4-6] ATK1 Daze Daze equal behavior "3D", which "Exploder and Friends" uses. MATCH.
- "Repulsor and Friends" and "A7 Repulsor and Friends" use "D3" (BoardGame.java:673, :684). NOT IN REFERENCE (swapped faces).
- Rewards: "Repulsor and Friends" gives 0 gold + potion (:84). The ref primary card for this encounter is missing. NOT IN REFERENCE.

### BGSpiker (spiker_a3_minion_a / _b)
- HP 10.
- Faces: '2' = ATK2; 'T' = +1 Thorns.
- Starts with 1 thorns, capped at 5 (powers/BGSpikerPower.java:40). MATCH.
- Behavior comes from BGTheBeyond.getSummonSpiker(), drawn from the pool "2T"/"T2". These equal minion_a and minion_b. MATCH.
- Thorns damage: the mod accumulates a "Procced" counter and deals it when the Spiker dies or later. Exact timing vs "after you play an Attack" not verified (inferred, possible MISMATCH in timing).

### BGSpireGrowth (spire_growth_a3)
- HP 28. MATCH.
- Turn 1 no action. MATCH (BGSpireGrowth.java:112-114).
- Faces:
  - [1-3] is move 1: 2 then 3.
  - [4-6] is move 2: 4 then 2.
  - Values MATCH (:70-73).
  - In both rows the ref says the second hit is AOE. The mod is single-target.
- Rewards: 1 gold + potion (MonsterGroupRewardsList.java:82). Ref is 1c_pot_card. MATCH.

### BGOrbWalker (orb_walker_a3)
- HP 22. MATCH.
- Moves:
  - Move 1: ATK2 + 1 Str.
  - Move 2: ATK3 + 1 Daze.
  - BGOrbWalkerPower adds +1 Str every round.
  - Effective result: move 1 = ATK2 Str Str, move 2 = ATK3 Daze Str. MATCH with the ref rows.
- "v3.2" maps 1-3→ATK3, which MATCHES the ref.
- "v2.3" has swapped faces. NOT IN REFERENCE.
- Rewards: both encounters give 1 gold (:85-86). Ref is 1c_card. MATCH.

### BGTransient (transient_a3)
- HP 99. MATCH.
- Attacks 6, 9, 12, 15 with FadingPower 4, so it dies after the 4th attack. MATCH (inferred from vanilla FadingPower semantics; BGTransient.java:61-74).
- Shift: the player gains Block equal to the HP lost. MATCH (powers/BGShiftingPower.java:22).
- Mod damage list continues to 18-30. This is unreachable.
- Rewards: 2 gold (:87). Ref is 2c_card. MATCH.

### BGWrithingMass (writhing_mass_a3)
- HP 27. MATCH.
- d6 faces (BGWrithingMass.java:165-177):
  - 1 = Vuln (move 3)
  - 2 = 2 Daze (move 1)
  - 3 = 3x2 (move 2)
  - 4 = 5 + 5 Block (move 0)
  - 5 = 7 (move 4)
  - 6 = 4 + Weak (move 5)
  - All MATCH.
- Reactive: power present. MATCH.
- "Acts last" on face 1: NOT IN MOD (inferred; no acts-last handling found in the class).
- Rewards: 0 gold + potion (MonsterGroupRewardsList.java:89). Ref is 2c_card. MISMATCH.

### BGMaw (no ref card)
- NOT IN REFERENCE.
- Mod values: HP 28.
- Sequence:
  - Move 1: Vuln (A7: + ATK2).
  - Then 2x2 → Str+2 → ATK6 (A7 8).
  - Then back to move 2.
- On the second pass the intent says 2x3 (:123), but the move still hits only multiCount=2 times. This is an internal inconsistency.
- Rewards: 1 gold.

### BGAwakenedOne (boss: Awakened One)
- HP 50 for phase 1 and phase 2. MATCH (A10 also 50).
- Phase 1: 3 → 5 (A10 6) → 2x2, looping. MATCH (BGAwakenedOne.java:126-132, :171-192).
- Phase 2: 7 (A10 6) → 4 + 2 Void → 3x2 + Str, looping to the 7 row.
  - Values MATCH.
  - The ref shows 2 Slimed plus the Void ability "pay to exhaust when drawn". The mod uses BGVoidCard (:224), which is functionally equivalent (inferred).
- Curiosity power: MATCH.
- A10 Rebirth: the mod gives Str equal to powersPlayedThisCombat (:199-203). Ref: Str equal to the Powers controlled by the player with the most Powers. MATCH-ish for solo (inferred).
- Summon: ref is 2 Cultists per player. Mod hard-codes 2 Cultists in the encounter.
- Rewards: 0 (:100), and rewardAllowed=false. Ref card has no reward badge (end of game). MATCH.

### BGTimeEater (boss: Time Eater)
- HP 60 (A10 64). MATCH (BGTimeEater.java:82).
- Sequence by turn%3:
  - 2x2 (A10 3x3)
  - 3 Slimed
  - 6 + Str
  - Time Warp clock set to 5/4/3. MATCH.
- Head Slam (move 2): MISMATCH at A0. The mod always adds 1 Daze (:137). Ref A0 is "AOE 6 + Str" with no Daze; only the A10 side adds Daze.
- Haste: heal 30 (A10 32) + Str + remove Weak/Vuln. MATCH (:200-204). Ref heal is per player.
- Rewards: 0 (:99). MATCH.

### BGDonu (boss: Donu) / BGDeca (boss: Deca)
- HP 50 (A10 55) for both. MATCH (BGDonu.java:73, BGDeca.java:73).
- Donu: Str to all enemies first, then 3x2, alternating. MATCH.
- Deca: 3x2 first, then Daze + Slimed (A10 2 Slimed), alternating. MATCH (BGDeca.java:138-139).
- The Deca card's 0-9 / 10-100 track is not modelled. Its purpose is unclear (inferred: an HP-overflow track).
- Rewards: 0. MATCH.

### BGCorruptHeart (boss: Corrupt Heart)
- HP 100 (A11 120). MATCH.
- Invincible floor 50 (A11 60). MATCH (BGCorruptHeart.java:86). Ref floor is per player.
- Beat of Death starts at 1. MATCH.
- First move (grey row): Weak + Vuln + 5 Slimed (A11 5 Burn) shuffled into the draw pile. MATCH (:96-107).
- Loop: 5 → 2x3 → 2 Str + Beat +1 (A11 +2) + lose Invincible. MATCH (:110-139, :157-171).
- Rewards: 0. MATCH.

### BGSpireShield (boss: Spire Shield)
- HP 30. MATCH (BGSpireShield.java:55). Ref scales 30/60/90/120.
- Moves: 20 Block → ATK8 → 2 Str to all enemies, looping. MATCH (:74-97).
- Facing per move: lose 1 Energy / NoDraw / your damage is 0. MATCH (:110-122).
- Row switching ("after start of turn, players can switch rows") is approximated by BGSurroundedPower/BGDifferentRowsPower. Exact behavior not verified.
- Rewards: "Shield and Spear" gives 0 gold + relic (MonsterGroupRewardsList.java:97). The ref Shield badge shows a chest plus a second icon (possibly a card). MATCH on relic (inferred).

### BGSpireSpear (boss: Spire Spear)
- HP 42. MATCH. Ref scales 42/84/126/168.
- Moves: 2x2 → 2 Daze → ATK9, looping. MATCH (BGSpireSpear.java:63-87).
- Facing: 2 Burn to the discard pile. MATCH (:99-102).
- The mod puts Spear in row 1 (MultiCreature currentRow, :48).

### Not covered here
BGCultist (Awakened One summons, Jaw Worms (Hard)), BGJawWorm and BGSphericGuardian (A7 Repulsor and Friends) are covered in the Act 1/2 sections. The "Jaw Worms (Hard)" 2-gold reward is listed at MonsterGroupRewardsList.java:80.

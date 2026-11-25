package BoardGame.multiplayer.combat;

import java.util.HashMap;

/**
 * Multiplayer data from the physical enemy cards (transcribed in the sts-tbg reference project;
 * see audit/enemy_mp_data.json): HP by number of players, and which moves hit every player.
 * Generated; edit the JSON and regenerate rather than editing by hand.
 */
public class EnemyMPData {
    /** HP for 1..4 players. */
    private static final HashMap<String, int[]> HP = new HashMap<>();
    /** Move bytes (the monster's nextMove) whose action hits all players. */
    private static final HashMap<String, int[]> AOE = new HashMap<>();
    /** Moves where only some hits hit all players: "class#move" -> indexes of player-targeted hits (0-based). */
    private static final HashMap<String, int[]> AOE_HITS = new HashMap<>();

    static {
        // only the second hit is "all players" on the card
        AOE_HITS.put("BGSpireGrowth#1", new int[]{1});
        AOE_HITS.put("BGSpireGrowth#2", new int[]{1});
        // A7 Snake Plant card (snake_plant_a2_asc7): 3, then AOE 2
        AOE_HITS.put("BGSnakePlant#3", new int[]{1});
        // A7 Large Slime (cube card): its splash attack hits all players
        AOE.put("BGAcidSlime_L", new int[]{6});
        HP.put("BGGremlinNob", new int[]{17, 33, 48, 64});
        HP.put("BGHexaghost", new int[]{36, 75, 112, 150});
        HP.put("BGLagavulin", new int[]{22, 44, 66, 88});
        HP.put("BGSlimeBoss", new int[]{22, 44, 66, 88});
        HP.put("BGTheGuardian", new int[]{40, 80, 120, 160});
        HP.put("BGBookOfStabbing", new int[]{35, 70, 105, 140});
        HP.put("BGTaskmaster", new int[]{15, 32, 48, 64});
        HP.put("BGGremlinLeader", new int[]{30, 60, 90, 120});
        HP.put("BGBronzeAutomaton", new int[]{55, 110, 165, 220});
        HP.put("BGTheCollector", new int[]{57, 114, 171, 228});
        HP.put("BGChamp", new int[]{40, 80, 120, 160});
        HP.put("BGGiantHead", new int[]{80, 160, 240, 320});
        HP.put("BGNemesis", new int[]{30, 60, 90, 120});
        HP.put("BGReptomancer", new int[]{35, 70, 105, 140});
        HP.put("BGAwakenedOne", new int[]{50, 100, 150, 200});
        HP.put("BGTimeEater", new int[]{60, 120, 180, 240});
        HP.put("BGDonu", new int[]{50, 100, 150, 200});
        HP.put("BGDeca", new int[]{50, 100, 150, 200});
        HP.put("BGCorruptHeart", new int[]{100, 200, 300, 400});
        HP.put("BGSpireShield", new int[]{30, 60, 90, 120});
        HP.put("BGSpireSpear", new int[]{42, 84, 126, 168});
        AOE.put("BGGremlinNob", new int[]{2});
        AOE.put("BGHexaghost", new int[]{1, 2, 3, 4, 5, 6});
        AOE.put("BGLagavulin", new int[]{1, 3, 7, 8, 9, 10, 11, 12});
        AOE.put("BGSlimeBoss", new int[]{1, 2, 3});
        AOE.put("BGTheGuardian", new int[]{2, 3, 4, 5});
        AOE.put("BGBookOfStabbing", new int[]{1, 2});
        AOE.put("BGTaskmaster", new int[]{0, 1, 2, 3});
        AOE.put("BGGremlinLeader", new int[]{1, 2});
        AOE.put("BGChosen", new int[]{1});
        AOE.put("BGBronzeAutomaton", new int[]{2, 4});
        AOE.put("BGTheCollector", new int[]{2, 3, 4});
        AOE.put("BGChamp", new int[]{1, 2, 3, 5});
        AOE.put("BGGiantHead", new int[]{1, 2, 4});
        AOE.put("BGNemesis", new int[]{0, 1, 2, 3});
        AOE.put("BGReptomancer", new int[]{1, 2, 3, 4});
        AOE.put("BGExploder", new int[]{3});
        AOE.put("BGWrithingMass", new int[]{1, 3});
        AOE.put("BGAwakenedOne", new int[]{0, 1, 2, 4, 5, 6});
        AOE.put("BGTimeEater", new int[]{0, 1, 2});
        AOE.put("BGDonu", new int[]{0});
        AOE.put("BGDeca", new int[]{0, 2});
        AOE.put("BGCorruptHeart", new int[]{0, 1, 2});
        AOE.put("BGSpireShield", new int[]{1});
        AOE.put("BGSpireSpear", new int[]{0, 1, 2});
    }

    /** HP for this many players, scaled from the solo HP the game already set (keeps ascension changes). */
    public static int scaledHp(String cls, int soloHp, int players) {
        int[] h = HP.get(cls);
        if (h == null || players <= 1) return soloHp;
        int i = Math.min(players, h.length) - 1;
        return Math.max(1, Math.round(soloHp * (float) h[i] / h[0]));
    }

    public static boolean hasScaledHp(String cls) {
        return HP.containsKey(cls);
    }

    public static boolean isAoeHit(String cls, int move, int hitIndex) {
        int[] a = AOE_HITS.get(cls + "#" + move);
        if (a == null) return false;
        for (int x : a) if (x == hitIndex) return true;
        return false;
    }

    public static boolean isAoe(String cls, int move) {
        int[] a = AOE.get(cls);
        if (a == null) return false;
        for (int x : a) if (x == move) return true;
        return false;
    }
}

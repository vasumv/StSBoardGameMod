package BoardGame.multiplayer.combat;

import BoardGame.actions.BGLockInRollAction;
import BoardGame.multiplayer.MP;
import BoardGame.multiplayer.Session;
import BoardGame.relics.BGTheDieRelic;
import BoardGame.thedie.TheDie;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.relics.AbstractRelic;

import java.util.HashMap;
import java.util.HashSet;

/**
 * The shared die, once per round:
 * 1. After everyone draws, the die is rolled (same result in every game) and shown to all.
 * 2. Anyone may change it with a die ability (Abacus, Toolbox, Gambling Chip, Gambler's Brew).
 *    Each change is shared and everyone has to confirm again.
 * 3. Everyone confirms ("Keep N"). Using a potion or ending the turn also confirms.
 * 4. Only when all players have confirmed do die relics trigger, on the final number.
 * Cards cannot be played until then. The solo-only Loaded Die ability is off in multiplayer.
 */
public class DiePhase {
    public static long roundKey = -1;   // round currently being decided (or decided)
    public static boolean decided = false;
    public static float rollAnim = 0f;  // seconds left of the rolling animation
    public static String lastChange = "";
    /** Die relics that triggered this round, per player. */
    public static final HashMap<Integer, String> triggered = new HashMap<>();
    private static boolean firstRollPending = false;
    private static boolean applyingRemote = false;
    private static boolean releasing = false;
    private static final HashMap<Long, HashSet<Integer>> locks = new HashMap<>();
    private static final HashMap<Long, Integer> pendingValue = new HashMap<>();

    private static boolean multi() {
        return MP.inRun && MP.playerCount() > 1 && MonsterSync.active();
    }

    public static long currentKey() {
        return AbstractDungeon.floorNum * 1000L + GameActionManager.turn;
    }

    /** True while the round's die is still being decided: no card plays yet. */
    public static boolean deciding() {
        return multi() && roundKey == currentKey() && !decided;
    }

    public static boolean iConfirmed() {
        HashSet<Integer> s = locks.get(roundKey);
        return s != null && s.contains(MP.myId);
    }

    public static boolean confirmed(int playerId) {
        HashSet<Integer> s = locks.get(roundKey);
        return s != null && s.contains(playerId);
    }

    public static void reset() {
        roundKey = -1;
        decided = false;
        locks.clear();
        pendingValue.clear();
        firstRollPending = false;
        lastChange = "";
    }

    // ---------------- rolling ----------------

    /** Called by TheDie.roll() instead of its random draw. */
    public static int roll(com.megacrit.cardcrawl.random.Random rng) {
        if (!MP.inRun || MP.playerCount() < 2) return rng.random(1, 6);
        long key = currentKey();
        if (key != roundKey) {
            // first roll of the round: the same number in every game
            roundKey = key;
            decided = false;
            firstRollPending = true;
            rollAnim = 0.9f;
            lastChange = "";
            long s = MP.seed * 1000003L + key * 7919L;
            return new java.util.Random(s).nextInt(6) + 1;
        }
        return rng.random(1, 6); // a reroll (Gambling Chip): shared below
    }

    @SpirePatch2(clz = TheDie.class, method = "roll")
    public static class SharedRoll {
        @SpireInstrumentPatch
        public static javassist.expr.ExprEditor edit() {
            return new javassist.expr.ExprEditor() {
                @Override
                public void edit(javassist.expr.MethodCall m) throws javassist.CannotCompileException {
                    if (m.getMethodName().equals("random")
                            && m.getClassName().equals("com.megacrit.cardcrawl.random.Random")) {
                        m.replace("{ $_ = BoardGame.multiplayer.combat.DiePhase.roll($0); }");
                    }
                }
            };
        }

        @SpirePostfixPatch
        public static void postfix() {
            if (!MP.inRun || MP.playerCount() < 2) return;
            if (firstRollPending) {
                firstRollPending = false;
                Integer v = pendingValue.remove(roundKey); // someone changed it before our roll arrived
                if (v != null) applyValue(v);
                triggered.clear();
                // nothing to decide without a die-changing relic or potion: keep it right away
                if (!BoardGame.ui.LockInRollButton.playerHasAnyDieChangingAbilities()) confirm();
                maybeRelease();
                return;
            }
            if (!applyingRemote) shareChange();
        }
    }

    @SpirePatch2(clz = TheDie.class, method = "tentativeRoll")
    public static class ShareTentative {
        @SpirePostfixPatch
        public static void postfix() {
            if (MP.inRun && MP.playerCount() > 1 && !applyingRemote) shareChange();
        }
    }

    private static void shareChange() {
        if (roundKey != currentKey() || decided) return;
        JsonObject o = MP.msg("die");
        o.addProperty("key", roundKey);
        o.addProperty("v", TheDie.monsterRoll);
        MP.send(o);
    }

    private static void applyValue(int v) {
        applyingRemote = true;
        try {
            TheDie.tentativeRoll(v);
        } finally {
            applyingRemote = false;
        }
    }

    // ---------------- confirming ----------------

    /** Every frame: with nothing that can change the die, there's nothing to decide. */
    public static void autoKeep() {
        if (deciding() && !iConfirmed() && AbstractDungeon.player != null
                && !(autoSentKey == roundKey && autoSentVersion == dieVersion)
                && !BoardGame.ui.LockInRollButton.playerHasAnyDieChangingAbilities()) {
            autoSentKey = roundKey; // once per number (the lock takes a moment to come back)
            autoSentVersion = dieVersion;
            confirm();
        }
    }

    private static long autoSentKey = -1;
    private static int autoSentVersion = -1, dieVersion = 0;

    /** Local player accepts the current number (button, potion, end turn...). */
    public static void confirm() {
        if (!deciding() || iConfirmed()) return;
        JsonObject o = MP.msg("lock");
        o.addProperty("key", roundKey);
        MP.send(o);
    }

    public static void onMessage(String type, int from, JsonObject m) {
        long key = m.get("key").getAsLong();
        if (type.equals("die")) {
            int v = m.get("v").getAsInt();
            locks.remove(key); // a new number: everyone confirms again
            dieVersion++;
            if (key == roundKey && !decided) {
                if (TheDie.monsterRoll != v) applyValue(v);
                // someone else's change is the new starting point for your own Abacus/Toolbox, and must
                // not count as you having used one of yours
                if (from != MP.myId) TheDie.initialRoll = v;
                if (!BoardGame.ui.LockInRollButton.playerHasAnyDieChangingAbilities()) confirm();
                rollAnim = 0.6f;
                lastChange = Session.nameOf(from) + " changed the die to " + v;
                Session.toast(lastChange);
            } else if (key > roundKey) {
                pendingValue.put(key, v);
            }
        } else if (type.equals("dietrig")) {
            if (key != roundKey) return;
            String list = m.get("list").getAsString();
            triggered.put(from, list);
        } else if (type.equals("lock")) {
            locks.computeIfAbsent(key, k -> new HashSet<>()).add(from);
            maybeRelease();
        }
    }

    public static void maybeRelease() {
        if (decided || roundKey != currentKey() || firstRollPending) return;
        HashSet<Integer> s = locks.get(roundKey);
        if (s == null) return;
        for (MP.Player p : MP.players) if (p.connected && !p.dead && !s.contains(p.id)) return;
        decided = true;
        AbstractRelic r = AbstractDungeon.player.getRelic(BGTheDieRelic.ID);
        if (r instanceof BGTheDieRelic) {
            releasing = true;
            try {
                TheDie.forceLockInRoll = true;
                ((BGTheDieRelic) r).lockRollAndActivateDieRelics();
            } finally {
                releasing = false;
            }
        }
        StringBuilder mine = new StringBuilder();
        for (AbstractRelic rel : AbstractDungeon.player.relics) {
            if (!(rel instanceof BoardGame.relics.DieControlledRelic) || rel instanceof BGTheDieRelic) continue;
            String q = ((BoardGame.relics.DieControlledRelic) rel).getQuickSummary();
            if (q == null || q.isEmpty()) continue;
            if (mine.length() > 0) mine.append(", ");
            mine.append(rel.name).append(" (").append(q.replaceAll("#[a-z]", "")).append(")");
        }
        JsonObject o = MP.msg("dietrig");
        o.addProperty("key", roundKey);
        o.addProperty("list", mine.toString());
        MP.send(o);
    }

    /** Die relics only trigger once everyone has confirmed. */
    @SpirePatch2(clz = BGTheDieRelic.class, method = "lockRollAndActivateDieRelics", paramtypez = {boolean.class})
    public static class HoldRelics {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix() {
            if (releasing || !multi() || roundKey != currentKey()) return SpireReturn.Continue();
            if (decided) return SpireReturn.Continue(); // already locked: the relic's own guard makes this a no-op
            confirm();
            return SpireReturn.Return();
        }
    }

    /** No automatic lock-in in multiplayer: everyone confirms. */
    @SpirePatch2(clz = BGLockInRollAction.class, method = "update")
    public static class NoAutoLock {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(BGLockInRollAction __instance) {
            if (!multi()) return SpireReturn.Continue();
            __instance.isDone = true;
            return SpireReturn.Return();
        }
    }

    /** The Loaded Die's own ability (Block on 4-5, copy a die relic on 6) is solo only. */
    @SpirePatch2(clz = BGTheDieRelic.class, method = "checkDieAbility")
    public static class NoLoadedDieInMultiplayer {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix() {
            return MP.inRun && MP.playerCount() > 1 ? SpireReturn.Return() : SpireReturn.Continue();
        }
    }

    @SpirePatch2(clz = BGTheDieRelic.class, method = "getQuickSummary")
    public static class NoLoadedDieSummary {
        @SpirePrefixPatch
        public static SpireReturn<String> prefix() {
            return MP.inRun && MP.playerCount() > 1 ? SpireReturn.Return("") : SpireReturn.Continue();
        }
    }
}

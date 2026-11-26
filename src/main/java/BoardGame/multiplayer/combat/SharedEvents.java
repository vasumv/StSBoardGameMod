package BoardGame.multiplayer.combat;

import BoardGame.events.BGBigFish;
import BoardGame.events.BGDeadAdventurer;
import BoardGame.events.BGLab;
import BoardGame.multiplayer.MP;
import BoardGame.multiplayer.Session;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.util.HashMap;

/**
 * Events the party resolves together:
 * - Dead Adventurer and the Lab use one party die roll: everyone gets the same result.
 * - Lab: the extra potion on a 4-6 goes to one player (the one in the bottom row).
 * - Big Fish: each player must take a different option.
 */
public class SharedEvents {
    private static int rollIndex = 0;
    private static int rollFloor = -1;
    private static final HashMap<Integer, Integer> fishClaims = new HashMap<>(); // option -> player
    private static int fishFloor = -1;
    private static boolean fishDriven = false;
    private static int fishWaiting = -1;

    private static boolean multi() {
        return MP.inRun && MP.playerCount() > 1;
    }

    /** The party's roll: the n-th roll at this event is the same number in every game. */
    public static int partyRoll(com.megacrit.cardcrawl.random.Random rng, int lo, int hi) {
        if (!multi()) return rng.random(lo, hi);
        if (rollFloor != AbstractDungeon.floorNum) {
            rollFloor = AbstractDungeon.floorNum;
            rollIndex = 0;
        }
        long s = MP.seed * 2654435761L + AbstractDungeon.floorNum * 104729L + AbstractDungeon.actNum * 7L + (rollIndex++);
        return lo + new java.util.Random(s).nextInt(hi - lo + 1);
    }

    private static ExprEditor sharedRolls() {
        return new ExprEditor() {
            @Override
            public void edit(MethodCall m) throws CannotCompileException {
                if (m.getMethodName().equals("random") && m.getClassName().equals("com.megacrit.cardcrawl.random.Random")
                        && m.getSignature().equals("(II)I")) {
                    m.replace("{ $_ = BoardGame.multiplayer.combat.SharedEvents.partyRoll($0, $1, $2);"
                            + " BoardGame.multiplayer.ui.EventDie.rolled($_, $1, $2); }");
                }
            }
        };
    }

    @SpirePatch2(clz = BGLab.class, method = "buttonEffect")
    public static class LabRoll {
        @SpireInstrumentPatch
        public static ExprEditor edit() {
            return sharedRolls();
        }
    }

    @SpirePatch2(clz = BGDeadAdventurer.class, method = "buttonEffect")
    public static class DeadAdventurerRoll {
        @SpireInstrumentPatch
        public static ExprEditor edit() {
            return sharedRolls();
        }
    }

    /** Lab: only one player takes the extra potion. */
    public static boolean labExtraPotionIsMine() {
        return !multi() || MP.myRow() == 0;
    }

    // ---------------- Big Fish: different options ----------------

    @SpirePatch2(clz = BGBigFish.class, method = "buttonEffect")
    public static class FishClaim {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(BGBigFish __instance, int buttonPressed) {
            Object screen = basemod.ReflectionHacks.getPrivate(__instance, BGBigFish.class, "screen");
            if (fishDriven || !multi() || !"INTRO".equals(String.valueOf(screen)) || buttonPressed > 2)
                return SpireReturn.Continue();
            resetFish();
            Integer who = fishClaims.get(buttonPressed);
            if (who != null && who != MP.myId) {
                Session.toast(Session.nameOf(who) + " already took that option");
                return SpireReturn.Return();
            }
            if (fishWaiting >= 0) return SpireReturn.Return();
            // the option is ours only if our claim comes back first in the shared order
            fishWaiting = buttonPressed;
            JsonObject o = MP.msg("fish");
            o.addProperty("floor", AbstractDungeon.floorNum);
            o.addProperty("opt", buttonPressed);
            MP.send(o);
            return SpireReturn.Return();
        }
    }

    private static void resetFish() {
        if (fishFloor != AbstractDungeon.floorNum) {
            fishFloor = AbstractDungeon.floorNum;
            fishClaims.clear();
        }
    }

    // ---------------- Ancient Temple: escalating HP cost ----------------

    private static final java.util.ArrayList<Integer> templeOrder = new java.util.ArrayList<>();
    private static int templeFloor = -1;

    private static void resetTemple() {
        if (templeFloor != AbstractDungeon.floorNum) {
            templeFloor = AbstractDungeon.floorNum;
            templeOrder.clear();
        }
    }

    public static void templeEnter() {
        if (!multi()) return;
        JsonObject o = MP.msg("temple");
        o.addProperty("floor", AbstractDungeon.floorNum);
        MP.send(o);
    }

    /** How many players went inside before you (in the shared message order). */
    public static int templeExtra() {
        if (!multi()) return 0;
        resetTemple();
        int i = templeOrder.indexOf(MP.myId);
        return i >= 0 ? i : templeOrder.size();
    }

    public static void onMessage(String type, int from, JsonObject m) {
        if (type.equals("temple")) {
            if (m.get("floor").getAsInt() != AbstractDungeon.floorNum) return;
            resetTemple();
            if (!templeOrder.contains(from)) templeOrder.add(from);
            if (from != MP.myId) Session.toast(Session.nameOf(from) + " went into the temple");
            return;
        }
        if (!type.equals("fish") || m.get("floor").getAsInt() != AbstractDungeon.floorNum) return;
        resetFish();
        int opt = m.get("opt").getAsInt();
        Integer first = fishClaims.putIfAbsent(opt, from);
        if (from == MP.myId) {
            fishWaiting = -1;
            if (first != null && first != MP.myId) {
                Session.toast(Session.nameOf(first) + " took that option first; pick another");
                return;
            }
            if (AbstractDungeon.getCurrRoom().event instanceof BGBigFish) {
                fishDriven = true;
                try {
                    java.lang.reflect.Method bm = BGBigFish.class.getDeclaredMethod("buttonEffect", int.class);
                    bm.setAccessible(true);
                    bm.invoke(AbstractDungeon.getCurrRoom().event, opt);
                } catch (Throwable t) {
                    MP.logger.error("Big Fish", t);
                } finally {
                    fishDriven = false;
                }
            }
            return;
        }
        if (first != null) return;
        try {
            if (AbstractDungeon.getCurrRoom().event instanceof BGBigFish) {
                AbstractImageEvent ev = (AbstractImageEvent) AbstractDungeon.getCurrRoom().event;
                if (opt < ev.imageEventText.optionList.size()) ev.imageEventText.optionList.get(opt).isDisabled = true;
            }
        } catch (Throwable ignored) {
        }
        Session.toast(Session.nameOf(from) + " took " + (opt >= 0 && opt < 4 ? new String[]{"the Banana", "the Donut", "the Box", "Restraint"}[opt] : "Big Fish option " + (opt + 1)));
    }
}

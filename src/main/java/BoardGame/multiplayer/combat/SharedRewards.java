package BoardGame.multiplayer.combat;

import BoardGame.multiplayer.MP;
import BoardGame.multiplayer.Session;
import basemod.ReflectionHacks;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.screens.select.BossRelicSelectScreen;
import com.megacrit.cardcrawl.vfx.ObtainKeyEffect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Party rules for boss relics and keys:
 * - Boss chest: reveal one boss relic per player + 1 (3 for two players); a relic one
 *   player takes is gone for the others (see Session "bossrelic").
 * - Ruby and Sapphire keys are a party decision (board game: everyone looks at the relics / the
 *   campfire and all agree). Taking the key or Recall is a vote: nothing is given up yet. When
 *   everyone has voted, each player gives up their relic / campfire action and gets the key. If
 *   anyone takes their relic or acts at the campfire instead, the vote is called off.
 */
public class SharedRewards {
    private static boolean multi() {
        return MP.inRun && MP.playerCount() > 1;
    }

    @SpirePatch2(clz = BossRelicSelectScreen.class, method = "open", paramtypez = {ArrayList.class})
    public static class MoreBossRelics {
        @SpirePostfixPatch
        public static void postfix(BossRelicSelectScreen __instance) {
            if (!multi()) return;
            int want = MP.playerCount() + 1;
            float y = (float) (Settings.HEIGHT / 2.0f - 260f * Settings.scale);
            int extra = 0;
            while (__instance.relics.size() < want) {
                AbstractRelic r = AbstractDungeon.returnRandomRelic(AbstractRelic.RelicTier.BOSS);
                float x = Settings.WIDTH / 2f + (extra - (want - 4) / 2f) * 220f * Settings.scale;
                r.spawn(x, y);
                r.hb.move(r.currentX, r.currentY);
                __instance.relics.add(r);
                extra++;
            }
        }
    }

    // ---------------- Last Stand: a won boss fight ends the run ----------------

    @SpirePatch2(clz = com.megacrit.cardcrawl.ui.buttons.ProceedButton.class, method = "goToNextDungeon")
    public static class LastStandEndsRun {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(com.megacrit.cardcrawl.ui.buttons.ProceedButton __instance) {
            if (!multi() || !BoardGame.multiplayer.Session.lastStandUsed) return SpireReturn.Continue();
            Session.toast("Last Stand: the Act is won, but the party can't go on. Victory!");
            try {
                java.lang.reflect.Method m = com.megacrit.cardcrawl.ui.buttons.ProceedButton.class
                        .getDeclaredMethod("goToVictoryRoomOrTheDoor");
                m.setAccessible(true);
                m.invoke(__instance);
            } catch (Throwable t) {
                MP.logger.error("could not end the run", t);
                return SpireReturn.Continue();
            }
            return SpireReturn.Return();
        }
    }

    // ---------------- keys ----------------

    private static final HashMap<String, HashSet<Integer>> keyVotes = new HashMap<>();
    private static boolean granting = false;

    @SpirePatch2(clz = ObtainKeyEffect.class, method = SpirePatch.CONSTRUCTOR)
    public static class PartyKeys {
        @SpirePostfixPatch
        public static void postfix(ObtainKeyEffect __instance, ObtainKeyEffect.KeyColor ___keyColor) {
            ObtainKeyEffect.KeyColor color = ___keyColor;
            if (!multi() || granting) return;
            if (color == ObtainKeyEffect.KeyColor.BLUE || color == ObtainKeyEffect.KeyColor.RED) {
                if (agreed.remove(color.name())) return; // the party voted for it (Recall gives it a moment later)
                Held.held.set(__instance, true); // only an agreed vote gives the Ruby/Sapphire key
                MP.logger.warn("[keys] dropped a " + color + " key outside a party vote");
                return;
            }
            if (color == ObtainKeyEffect.KeyColor.GREEN) {
                // the Emerald Key (burning elite) is the party's: whoever picks it up, everyone has it
                JsonObject o = MP.msg("key");
                o.addProperty("color", color.name());
                o.addProperty("floor", AbstractDungeon.floorNum);
                o.addProperty("now", true);
                MP.send(o);
                return;
            }
            // hold the key until the whole party has done the same
            Held.held.set(__instance, true);
            JsonObject o = MP.msg("key");
            o.addProperty("color", color.name());
            o.addProperty("floor", AbstractDungeon.floorNum);
            MP.send(o);
        }
    }

    @SpirePatch(clz = ObtainKeyEffect.class, method = SpirePatch.CLASS)
    public static class Held {
        public static SpireField<Boolean> held = new SpireField<>(() -> false);
    }

    @SpirePatch2(clz = ObtainKeyEffect.class, method = "update")
    public static class HoldKey {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(ObtainKeyEffect __instance) {
            if (!Held.held.get(__instance)) return SpireReturn.Continue();
            __instance.isDone = true;
            return SpireReturn.Return();
        }
    }

    public static void onMessage(String type, int from, JsonObject m) {
        if (onVoteMessage(from, m)) return;
        String color = m.get("color").getAsString();
        if (m.has("now") && m.get("now").getAsBoolean()) {
            if (from == MP.myId || com.megacrit.cardcrawl.core.Settings.hasEmeraldKey) return;
            granting = true;
            try {
                AbstractDungeon.topLevelEffects.add(new ObtainKeyEffect(ObtainKeyEffect.KeyColor.valueOf(color)));
            } finally {
                granting = false;
            }
            // its reward is gone from this game's screen too
            if (AbstractDungeon.combatRewardScreen != null && AbstractDungeon.combatRewardScreen.rewards != null) {
                AbstractDungeon.combatRewardScreen.rewards.removeIf(r ->
                        r.type == com.megacrit.cardcrawl.rewards.RewardItem.RewardType.EMERALD_KEY);
                AbstractDungeon.combatRewardScreen.positionRewards();
            }
            Session.toast(Session.nameOf(from) + " took the emerald key for the party");
            return;
        }
        String k = color + "@" + m.get("floor").getAsInt();
        HashSet<Integer> s = keyVotes.computeIfAbsent(k, x -> new HashSet<>());
        s.add(from);
        for (MP.Player p : MP.players) if (p.connected && !s.contains(p.id)) {
            if (from != MP.myId) Session.toast(Session.nameOf(from) + " is going for the " + color.toLowerCase()
                    + " key; everyone has to");
            return;
        }
        keyVotes.remove(k);
        granting = true;
        try {
            ObtainKeyEffect.KeyColor c = ObtainKeyEffect.KeyColor.valueOf(color);
            AbstractDungeon.topLevelEffects.add(new ObtainKeyEffect(c));
        } finally {
            granting = false;
        }
        Session.toast("The party has the " + color.toLowerCase() + " key");
    }

    // ---------------- Ruby / Sapphire: a vote before anyone gives anything up ----------------

    private static final HashSet<String> myVotes = new HashSet<>();
    /** Keys the party agreed on whose effect hasn't been created yet. */
    private static final HashSet<String> agreed = new HashSet<>();
    /** "color@floor#player": that player took the relic / acted at the campfire, so can't vote for the key. */
    private static final HashSet<String> calledOff = new HashSet<>();

    private static String voteKey(String color) {
        return color + "@" + AbstractDungeon.floorNum;
    }

    private static void vote(String color) {
        String k = voteKey(color);
        if (myVotes.contains(k)) return;
        myVotes.add(k);
        JsonObject o = MP.msg("key");
        o.addProperty("color", color);
        o.addProperty("floor", AbstractDungeon.floorNum);
        o.addProperty("vote", true);
        MP.send(o);
    }

    private static void callOff(String color, String why) {
        JsonObject o = MP.msg("key");
        o.addProperty("color", color);
        o.addProperty("floor", AbstractDungeon.floorNum);
        o.addProperty("cancel", why);
        MP.send(o);
    }

    private static boolean votePending(String color) {
        HashSet<Integer> s = keyVotes.get(voteKey(color));
        return s != null && !s.isEmpty();
    }

    /** Message handling for the vote (called from onMessage). Returns true if handled. */
    private static boolean onVoteMessage(int from, JsonObject m) {
        String color = m.get("color").getAsString();
        String k = color + "@" + m.get("floor").getAsInt();
        if (m.has("cancel")) {
            HashSet<Integer> had = keyVotes.remove(k);
            myVotes.remove(k);
            calledOff.add(k + "#" + from);
            if (had != null && !had.isEmpty()) // only news if someone wanted the key
                Session.toast(Session.nameOf(from) + " " + m.get("cancel").getAsString() + ": no "
                        + keyName(color) + " this time");
            return true;
        }
        if (!m.has("vote")) return false;
        if (calledOff.contains(k + "#" + from)) return true; // that player already chose the relic / campfire
        for (MP.Player p : MP.players)
            if (calledOff.contains(k + "#" + p.id)) {
                myVotes.remove(k);
                if (from == MP.myId) Session.toast(Session.nameOf(p.id) + " already "
                        + (color.equals("RED") ? "acted at the campfire" : "took the relic") + ": no " + keyName(color));
                return true;
            }
        HashSet<Integer> s = keyVotes.computeIfAbsent(k, x -> new HashSet<>());
        s.add(from);
        StringBuilder waiting = new StringBuilder();
        for (MP.Player p : MP.players) {
            if (!p.connected || p.dead || s.contains(p.id)) continue;
            if (waiting.length() > 0) waiting.append(", ");
            waiting.append(p.id == MP.myId ? "you" : p.name);
        }
        if (waiting.length() > 0) {
            Session.toast(Session.nameOf(from) + " wants the " + keyName(color) + " (" + (color.equals("RED")
                    ? "Recall at this campfire" : "skip the relic") + "). Waiting for " + waiting);
            return true;
        }
        keyVotes.remove(k);
        myVotes.remove(k);
        if (m.get("floor").getAsInt() == AbstractDungeon.floorNum) grantVoted(color);
        Session.toast("The party has the " + keyName(color));
        return true;
    }

    private static String keyName(String color) {
        return color.equals("RED") ? "Ruby Key" : color.equals("BLUE") ? "Sapphire Key" : "Emerald Key";
    }

    /** Everyone agreed: give up this game's relic / campfire action and take the key. */
    private static void grantVoted(String color) {
        agreed.add(color);
        granting = true;
        try {
            if (color.equals("RED")) {
                new com.megacrit.cardcrawl.ui.campfire.RecallOption().useOption();
                if (AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.RestRoom)
                    ((com.megacrit.cardcrawl.rooms.RestRoom) AbstractDungeon.getCurrRoom()).campfireUI.somethingSelected = true;
                return;
            }
            com.megacrit.cardcrawl.rewards.RewardItem key = null;
            if (AbstractDungeon.combatRewardScreen != null && AbstractDungeon.combatRewardScreen.rewards != null)
                for (com.megacrit.cardcrawl.rewards.RewardItem r : AbstractDungeon.combatRewardScreen.rewards)
                    if (r.type == com.megacrit.cardcrawl.rewards.RewardItem.RewardType.SAPPHIRE_KEY && !r.isDone) key = r;
            if (key != null) {
                key.claimReward();
                key.isDone = true;
                if (key.relicLink != null) { // the relic it was linked to is given up
                    key.relicLink.isDone = true;
                    key.relicLink.ignoreReward = true;
                    AbstractDungeon.combatRewardScreen.rewards.remove(key.relicLink);
                }
                AbstractDungeon.combatRewardScreen.rewards.remove(key);
                AbstractDungeon.combatRewardScreen.positionRewards();
            } else if (!Settings.hasSapphireKey) {
                AbstractDungeon.topLevelEffects.add(new ObtainKeyEffect(ObtainKeyEffect.KeyColor.BLUE));
            }
            agreed.remove("BLUE"); // given right here
        } finally {
            granting = false;
        }
    }

    /** Taking the Sapphire Key is a vote; taking its linked relic calls a pending vote off. */
    @SpirePatch2(clz = com.megacrit.cardcrawl.rewards.RewardItem.class, method = "claimReward")
    public static class SapphireVote {
        @SpirePrefixPatch
        public static SpireReturn<Boolean> prefix(com.megacrit.cardcrawl.rewards.RewardItem __instance) {
            if (!multi() || granting) return SpireReturn.Continue();
            // ignoreReward: the game is removing the other half of a linked pair, not a player's choice
            if (__instance.ignoreReward) return SpireReturn.Continue();
            if (__instance.type == com.megacrit.cardcrawl.rewards.RewardItem.RewardType.SAPPHIRE_KEY) {
                __instance.isDone = false; // stays on the screen: nothing is given up until everyone agrees
                if (!calledOff.contains(voteKey("BLUE") + "#" + MP.myId)) vote("BLUE");
                return SpireReturn.Return(false);
            }
            if (__instance.relicLink != null
                    && __instance.relicLink.type == com.megacrit.cardcrawl.rewards.RewardItem.RewardType.SAPPHIRE_KEY) {
                myVotes.remove(voteKey("BLUE"));
                callOff("BLUE", "took the relic");
            }
            return SpireReturn.Continue();
        }
    }

    // campfire: Recall is a vote; any other campfire action calls a pending vote off
    private static boolean heldCampfire = false;

    public static boolean allowCampfire(com.megacrit.cardcrawl.ui.campfire.AbstractCampfireOption opt) {
        if (!multi() || granting) return true;
        if (opt instanceof com.megacrit.cardcrawl.ui.campfire.RecallOption) {
            vote("RED");
            heldCampfire = true;
            return false;
        }
        myVotes.remove(voteKey("RED"));
        callOff("RED", "chose another action at the campfire");
        return true;
    }

    @SpirePatch2(clz = com.megacrit.cardcrawl.ui.campfire.AbstractCampfireOption.class, method = "update")
    public static class RecallVote {
        @SpireInstrumentPatch
        public static javassist.expr.ExprEditor edit() {
            return new javassist.expr.ExprEditor() {
                @Override
                public void edit(javassist.expr.MethodCall mc) throws javassist.CannotCompileException {
                    if (mc.getMethodName().equals("useOption"))
                        mc.replace("{ if (BoardGame.multiplayer.combat.SharedRewards.allowCampfire($0)) $proceed($$); }");
                }
            };
        }

        @SpirePostfixPatch
        public static void postfix() {
            if (!heldCampfire) return;
            heldCampfire = false; // keep the campfire open: the Recall isn't done until everyone agrees
            if (AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.RestRoom)
                ((com.megacrit.cardcrawl.rooms.RestRoom) AbstractDungeon.getCurrRoom()).campfireUI.somethingSelected = false;
        }
    }
}

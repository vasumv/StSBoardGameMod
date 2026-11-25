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
 * - Ruby and Sapphire keys need the whole party: everyone recalls at the same campfire /
 *   everyone takes the key at the same chest. The key is granted once all have.
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

    // ---------------- keys ----------------

    private static final HashMap<String, HashSet<Integer>> keyVotes = new HashMap<>();
    private static boolean granting = false;

    @SpirePatch2(clz = ObtainKeyEffect.class, method = SpirePatch.CONSTRUCTOR)
    public static class PartyKeys {
        @SpirePostfixPatch
        public static void postfix(ObtainKeyEffect __instance, ObtainKeyEffect.KeyColor ___keyColor) {
            ObtainKeyEffect.KeyColor color = ___keyColor;
            if (!multi() || granting || color == ObtainKeyEffect.KeyColor.GREEN) return;
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
        String color = m.get("color").getAsString();
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
}

package BoardGame.multiplayer.combat;

import BoardGame.multiplayer.MP;
import BoardGame.multiplayer.Session;
import basemod.ReflectionHacks;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PotionHelper;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.shop.ShopScreen;
import com.megacrit.cardcrawl.shop.StorePotion;
import com.megacrit.cardcrawl.shop.StoreRelic;

import java.util.ArrayList;

/**
 * One merchant for the party: the host's 3 relics and 3 potions are shown in every game,
 * and an item one player buys is gone for everyone. Card offers and the card removal
 * stay per player, as in the board game.
 */
public class SharedShop {
    private static JsonObject pendingStock;
    private static boolean applying = false;

    private static boolean multi() {
        return MP.inRun && MP.playerCount() > 1;
    }

    private static long key() {
        return AbstractDungeon.floorNum * 100L + AbstractDungeon.actNum;
    }

    @SuppressWarnings("unchecked")
    private static ArrayList<StoreRelic> relics(ShopScreen s) {
        return (ArrayList<StoreRelic>) ReflectionHacks.getPrivate(s, ShopScreen.class, "relics");
    }

    @SuppressWarnings("unchecked")
    private static ArrayList<StorePotion> potions(ShopScreen s) {
        return (ArrayList<StorePotion>) ReflectionHacks.getPrivate(s, ShopScreen.class, "potions");
    }

    @SpirePatch2(clz = ShopScreen.class, method = "init")
    public static class ShareStock {
        @SpirePostfixPatch
        public static void postfix(ShopScreen __instance) {
            if (!multi()) return;
            if (MP.isHost()) {
                JsonObject o = MP.msg("shopstock");
                o.addProperty("key", key());
                JsonArray rs = new JsonArray(), ps = new JsonArray();
                for (StoreRelic r : relics(__instance)) {
                    JsonObject j = new JsonObject();
                    j.addProperty("id", r.relic.relicId);
                    j.addProperty("price", r.price);
                    rs.add(j);
                }
                for (StorePotion p : potions(__instance)) {
                    JsonObject j = new JsonObject();
                    j.addProperty("id", p.potion.ID);
                    j.addProperty("price", p.price);
                    ps.add(j);
                }
                o.add("relics", rs);
                o.add("potions", ps);
                MP.send(o);
            } else if (pendingStock != null && pendingStock.get("key").getAsLong() == key()) {
                apply(__instance, pendingStock);
                pendingStock = null;
            }
        }
    }

    public static void onMessage(String type, int from, JsonObject m) {
        if (type.equals("shopstock")) {
            if (from == MP.myId) return;
            ShopScreen s = AbstractDungeon.shopScreen;
            boolean open = s != null && relics(s) != null && AbstractDungeon.getCurrRoom()
                    instanceof com.megacrit.cardcrawl.rooms.ShopRoom && m.get("key").getAsLong() == key();
            if (open) apply(s, m);
            else pendingStock = m;
        } else if (type.equals("shopbuy")) {
            if (from == MP.myId) return;
            ShopScreen s = AbstractDungeon.shopScreen;
            if (s == null || m.get("key").getAsLong() != key()) return;
            int slot = m.get("slot").getAsInt();
            String id = m.get("id").getAsString();
            if (m.get("kind").getAsString().equals("relic")) {
                for (StoreRelic r : relics(s)) if (r.relic.relicId.equals(id)) r.isPurchased = true;
            } else {
                for (StorePotion p : potions(s)) if (p.potion.ID.equals(id)) { p.isPurchased = true; break; }
            }
            Session.toast(Session.nameOf(from) + " bought " + id.replace("BoardGame:", ""));
        }
    }

    private static void apply(ShopScreen s, JsonObject m) {
        applying = true;
        try {
            ArrayList<StoreRelic> rs = relics(s);
            JsonArray hr = m.getAsJsonArray("relics");
            for (int i = 0; i < Math.min(rs.size(), hr.size()); i++) {
                JsonObject j = hr.get(i).getAsJsonObject();
                AbstractRelic r = RelicLibrary.getRelic(j.get("id").getAsString());
                if (r == null) continue;
                rs.get(i).relic = r.makeCopy();
                rs.get(i).price = j.get("price").getAsInt();
            }
            ArrayList<StorePotion> ps = potions(s);
            JsonArray hp = m.getAsJsonArray("potions");
            for (int i = 0; i < Math.min(ps.size(), hp.size()); i++) {
                JsonObject j = hp.get(i).getAsJsonObject();
                AbstractPotion p = PotionHelper.getPotion(j.get("id").getAsString());
                if (p == null) continue;
                ps.get(i).potion = p;
                ps.get(i).price = j.get("price").getAsInt();
            }
        } finally {
            applying = false;
        }
    }

    @SpirePatch2(clz = StoreRelic.class, method = "purchaseRelic")
    public static class ShareRelicBuy {
        @SpirePostfixPatch
        public static void postfix(StoreRelic __instance) {
            if (multi() && __instance.isPurchased) send("relic", __instance.relic.relicId);
        }
    }

    @SpirePatch2(clz = StorePotion.class, method = "purchasePotion")
    public static class SharePotionBuy {
        @SpirePostfixPatch
        public static void postfix(StorePotion __instance) {
            if (multi() && __instance.isPurchased) send("potion", __instance.potion.ID);
        }
    }

    private static void send(String kind, String id) {
        JsonObject o = MP.msg("shopbuy");
        o.addProperty("key", key());
        o.addProperty("kind", kind);
        o.addProperty("id", id);
        o.addProperty("slot", -1);
        MP.send(o);
    }
}

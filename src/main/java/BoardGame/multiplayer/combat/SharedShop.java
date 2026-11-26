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
    private static long shopInitKey = -1;

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
            shopInitKey = key();
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
                // colorless cards come from one shared deck too: everyone sees the host's
                JsonArray cs = new JsonArray();
                for (int list = 0; list < 2; list++) {
                    ArrayList<com.megacrit.cardcrawl.cards.AbstractCard> l = list == 0 ? __instance.coloredCards
                            : __instance.colorlessCards;
                    for (int i = 0; i < l.size(); i++) {
                        com.megacrit.cardcrawl.cards.AbstractCard c = l.get(i);
                        if (!sharedCard(c)) continue;
                        JsonObject j = new JsonObject();
                        j.addProperty("list", list);
                        j.addProperty("i", i);
                        j.addProperty("id", c.cardID);
                        j.addProperty("up", c.timesUpgraded);
                        j.addProperty("price", c.price);
                        cs.add(j);
                    }
                }
                o.add("colorless", cs);
                MP.send(o);
            } else if (pendingStock != null && pendingStock.get("key").getAsLong() == key()) {
                apply(__instance, pendingStock);
                pendingStock = null;
            }
            removeBoughtColorless(__instance);
        }
    }

    public static void onMessage(String type, int from, JsonObject m) {
        if (type.equals("shopstock")) {
            if (from == MP.myId) return;
            ShopScreen s = AbstractDungeon.shopScreen;
            // also covers events that turn the room into a shop (The Merchant)
            boolean open = s != null && relics(s) != null && !relics(s).isEmpty()
                    && m.get("key").getAsLong() == key() && shopInitKey == key();
            if (open) apply(s, m);
            else pendingStock = m;
        } else if (type.equals("shopcard")) {
            if (from == MP.myId || m.get("key").getAsLong() != key()) return;
            if (boughtKey != key()) {
                boughtColorless.clear();
                boughtKey = key();
            }
            boughtColorless.add(m.get("id").getAsString());
            ShopScreen s = AbstractDungeon.shopScreen;
            if (s != null && shopInitKey == key()) removeBoughtColorless(s);
            Session.toast(Session.nameOf(from) + " bought " + m.get("name").getAsString());
        } else if (type.equals("shopbuy")) {
            if (m.get("key").getAsLong() != key()) return;
            onClaim(from, m.get("kind").getAsString(), m.get("id").getAsString(), m.get("slot").getAsInt());
        } else if (type.equals("shoprelease")) {
            if (m.get("key").getAsLong() != key() || from == MP.myId) return;
            String k = m.get("kind").getAsString() + ":" + m.get("slot").getAsInt();
            claims.remove(k);
            ShopScreen s = AbstractDungeon.shopScreen;
            int slot = m.get("slot").getAsInt();
            if (s == null) return;
            if (m.get("kind").getAsString().equals("relic")) {
                if (slot >= 0 && slot < relics(s).size()) relics(s).get(slot).isPurchased = false;
            } else if (slot >= 0 && slot < potions(s).size()) potions(s).get(slot).isPurchased = false;
        }
    }

    private static final ArrayList<String> boughtColorless = new ArrayList<>();
    private static long boughtKey = -1;

    /** Take colorless cards a teammate bought out of this shop. */
    private static void removeBoughtColorless(ShopScreen s) {
        if (boughtKey != key()) return;
        ArrayList<String> left = new ArrayList<>(boughtColorless);
        for (ArrayList<com.megacrit.cardcrawl.cards.AbstractCard> l : java.util.Arrays.asList(s.coloredCards, s.colorlessCards)) {
            java.util.Iterator<com.megacrit.cardcrawl.cards.AbstractCard> it = l.iterator();
            while (it.hasNext()) {
                com.megacrit.cardcrawl.cards.AbstractCard c = it.next();
                if (sharedCard(c) && left.remove(c.cardID)) it.remove();
            }
        }
    }

    /** Colorless cards come from the party's one colorless deck. */
    private static boolean sharedCard(com.megacrit.cardcrawl.cards.AbstractCard c) {
        return c != null && c.color == BoardGame.characters.BGColorless.Enums.CARD_COLOR;
    }

    @SpirePatch2(clz = ShopScreen.class, method = "purchaseCard")
    public static class ShareColorlessBuy {
        @SpirePrefixPatch
        public static void prefix(ShopScreen __instance, com.megacrit.cardcrawl.cards.AbstractCard hoveredCard) {
            if (!multi() || !sharedCard(hoveredCard)) return;
            if (AbstractDungeon.player.gold < hoveredCard.price) return;
            JsonObject o = MP.msg("shopcard");
            o.addProperty("key", key());
            o.addProperty("id", hoveredCard.cardID);
            o.addProperty("name", hoveredCard.name);
            MP.send(o);
        }
    }

    private static void apply(ShopScreen s, JsonObject m) {
        applying = true;
        try {
            if (m.has("colorless")) {
                JsonArray hc = m.getAsJsonArray("colorless");
                for (int k = 0; k < hc.size(); k++) {
                    JsonObject j = hc.get(k).getAsJsonObject();
                    ArrayList<com.megacrit.cardcrawl.cards.AbstractCard> cs = j.get("list").getAsInt() == 0
                            ? s.coloredCards : s.colorlessCards;
                    int i = j.get("i").getAsInt();
                    if (i >= cs.size() || !sharedCard(cs.get(i))) continue;
                    com.megacrit.cardcrawl.cards.AbstractCard base =
                            com.megacrit.cardcrawl.helpers.CardLibrary.getCard(j.get("id").getAsString());
                    if (base == null) continue;
                    com.megacrit.cardcrawl.cards.AbstractCard c = base.makeCopy(), old = cs.get(i);
                    for (int u = 0; u < j.get("up").getAsInt() && c.canUpgrade(); u++) c.upgrade();
                    c.price = j.get("price").getAsInt();
                    c.current_x = old.current_x;
                    c.current_y = old.current_y;
                    c.target_x = old.target_x;
                    c.target_y = old.target_y;
                    c.drawScale = old.drawScale;
                    c.targetDrawScale = old.targetDrawScale;
                    cs.set(i, c);
                }
            }
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

    private static boolean buying = false;
    private static final java.util.HashMap<String, Integer> claims = new java.util.HashMap<>();
    private static long claimsKey = -1;

    @SpirePatch2(clz = StoreRelic.class, method = "purchaseRelic")
    public static class ClaimRelic {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(StoreRelic __instance) {
            if (buying || !multi()) return SpireReturn.Continue();
            if (AbstractDungeon.player.gold < __instance.price) return SpireReturn.Continue(); // the game says no
            send("relic", __instance.relic.relicId, relics(AbstractDungeon.shopScreen).indexOf(__instance));
            return SpireReturn.Return();
        }
    }

    @SpirePatch2(clz = StorePotion.class, method = "purchasePotion")
    public static class ClaimPotion {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(StorePotion __instance) {
            if (buying || !multi()) return SpireReturn.Continue();
            if (AbstractDungeon.player.gold < __instance.price) return SpireReturn.Continue();
            boolean freeSlot = false;
            for (com.megacrit.cardcrawl.potions.AbstractPotion p : AbstractDungeon.player.potions)
                freeSlot |= p instanceof com.megacrit.cardcrawl.potions.PotionSlot;
            if (!freeSlot || AbstractDungeon.player.hasRelic("BGSozu") || AbstractDungeon.player.hasRelic("Sozu"))
                return SpireReturn.Continue(); // the game explains why it can't be bought
            send("potion", __instance.potion.ID, potions(AbstractDungeon.shopScreen).indexOf(__instance));
            return SpireReturn.Return();
        }
    }

    /** A purchase claim in the shared order: the first claimer buys, everyone else sees it sold. */
    private static void onClaim(int from, String kind, String id, int slot) {
        if (claimsKey != key()) {
            claimsKey = key();
            claims.clear();
        }
        String k = kind + ":" + slot;
        Integer first = claims.putIfAbsent(k, from);
        ShopScreen s = AbstractDungeon.shopScreen;
        if (s == null) return;
        if (from == MP.myId) {
            if (first != null && first != MP.myId) {
                Session.toast(Session.nameOf(first) + " bought that first");
                return;
            }
            boolean bought = false;
            buying = true;
            try {
                if (kind.equals("relic")) {
                    ArrayList<StoreRelic> rs = relics(s);
                    if (slot >= 0 && slot < rs.size() && !rs.get(slot).isPurchased) {
                        rs.get(slot).purchaseRelic();
                        bought = rs.get(slot).isPurchased;
                    }
                } else {
                    ArrayList<StorePotion> ps = potions(s);
                    if (slot >= 0 && slot < ps.size() && !ps.get(slot).isPurchased) {
                        ps.get(slot).purchasePotion();
                        bought = ps.get(slot).isPurchased;
                    }
                }
            } finally {
                buying = false;
            }
            if (!bought) { // couldn't buy after all: give the item back to the shop for everyone
                JsonObject o = MP.msg("shoprelease");
                o.addProperty("key", key());
                o.addProperty("kind", kind);
                o.addProperty("slot", slot);
                MP.send(o);
            }
            return;
        }
        if (first != null) return;
        if (kind.equals("relic")) {
            if (slot >= 0 && slot < relics(s).size()) relics(s).get(slot).isPurchased = true;
        } else {
            if (slot >= 0 && slot < potions(s).size()) potions(s).get(slot).isPurchased = true;
        }
        Session.toast(Session.nameOf(from) + " bought " + id.replace("BoardGame:", ""));
    }

    private static void send(String kind, String id, int slot) {
        JsonObject o = MP.msg("shopbuy");
        o.addProperty("key", key());
        o.addProperty("kind", kind);
        o.addProperty("id", id);
        o.addProperty("slot", slot);
        MP.send(o);
    }
}

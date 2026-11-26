package BoardGame.multiplayer.combat;

import BoardGame.dungeons.AbstractBGDungeon;
import BoardGame.multiplayer.MP;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;

import java.util.ArrayList;

/**
 * The colorless and curse decks are single physical decks shared by the party. Every game
 * builds them in the same shuffled order at the start of the run; after that, a card drawn
 * or taken in one game is drawn or taken in every game, so nobody sees the same card twice.
 * (Each character's own reward and rare decks stay personal, as in the board game.)
 */
public class SharedDecks {
    /** While > 0, draws aren't shared (a client building its copy of the host's merchant). */
    public static int quiet = 0;
    private static boolean applying = false;

    private static boolean multi() {
        return MP.inRun && MP.playerCount() > 1 && !applying && quiet == 0;
    }

    private static String code(CardGroup g) {
        if (g == AbstractBGDungeon.colorlessRewardDeck) return "c";
        if (g == AbstractBGDungeon.cursesRewardDeck) return "x";
        return null;
    }

    private static CardGroup deck(String code) {
        return code.equals("c") ? AbstractBGDungeon.colorlessRewardDeck : AbstractBGDungeon.cursesRewardDeck;
    }

    /** A card was drawn from a shared deck in this game. */
    public static void drew(CardGroup g, AbstractCard c) {
        String k = code(g);
        if (k == null || c == null || !multi()) return;
        JsonObject o = MP.msg("sdraw");
        o.addProperty("deck", k);
        o.addProperty("id", c.cardID);
        MP.send(o);
    }

    /** A card was taken out of a shared deck for good in this game. */
    public static void took(CardGroup g, String cardId) {
        String k = code(g);
        if (k == null || !multi()) return;
        JsonObject o = MP.msg("stake");
        o.addProperty("deck", k);
        o.addProperty("id", cardId);
        MP.send(o);
    }

    public static void onMessage(String type, int from, JsonObject m) {
        if (from == MP.myId) return;
        CardGroup g = deck(m.get("deck").getAsString());
        String id = m.get("id").getAsString();
        applying = true;
        try {
            if (type.equals("sdraw")) {
                // the same card goes from the top to the bottom here too
                AbstractCard top = g.isEmpty() ? null : g.getTopCard();
                if (top != null && top.cardID.equals(id)) {
                    g.removeTopCard();
                    g.addToBottom(top);
                } else {
                    for (AbstractCard c : new ArrayList<>(g.group)) if (c.cardID.equals(id)) {
                        g.group.remove(c);
                        g.addToBottom(c);
                        break;
                    }
                    MP.logger.info("shared deck " + m.get("deck").getAsString() + " was out of step at " + id);
                }
            } else if (type.equals("stake")) {
                g.removeCard(id);
            }
        } finally {
            applying = false;
        }
    }

    // ---- saving the deck order with the party save (the game itself doesn't save these decks) ----

    public static String encode(CardGroup g) {
        StringBuilder sb = new StringBuilder();
        for (AbstractCard c : g.group) {
            if (sb.length() > 0) sb.append('|');
            sb.append(c.cardID).append('#').append(c.timesUpgraded);
        }
        return sb.toString();
    }

    private static void decodeInto(CardGroup g, String s) {
        if (s == null) return;
        ArrayList<AbstractCard> out = new ArrayList<>();
        if (!s.isEmpty()) for (String part : s.split("\\|")) {
            int h = part.lastIndexOf('#');
            AbstractCard base = com.megacrit.cardcrawl.helpers.CardLibrary.getCard(h < 0 ? part : part.substring(0, h));
            if (base == null) {
                MP.logger.error("saved deck has an unknown card " + part);
                return; // keep the rebuilt deck rather than a broken one
            }
            AbstractCard c = base.makeCopy();
            int up = h < 0 ? 0 : Integer.parseInt(part.substring(h + 1));
            for (int i = 0; i < up && c.canUpgrade(); i++) c.upgrade();
            out.add(c);
        }
        g.group.clear();
        g.group.addAll(out);
    }

    /** This game's decks: own reward and rare decks, and the shared colorless and curse decks. */
    public static JsonObject save() {
        JsonObject o = new JsonObject();
        o.addProperty("reward", encode(AbstractBGDungeon.rewardDeck));
        o.addProperty("rare", encode(AbstractBGDungeon.rareRewardDeck));
        o.addProperty("colorless", encode(AbstractBGDungeon.colorlessRewardDeck));
        o.addProperty("curses", encode(AbstractBGDungeon.cursesRewardDeck));
        return o;
    }

    /** Set on Continue: own decks from my save, shared decks from the host's. */
    public static JsonObject pendingOwn, pendingShared;

    @com.evacipated.cardcrawl.modthespire.lib.SpirePatch2(clz = com.megacrit.cardcrawl.dungeons.AbstractDungeon.class,
            method = "initializeCardPools")
    public static class RestoreOnContinue {
        @com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch
        public static void postfix() {
            try {
                if (pendingOwn != null) {
                    decodeInto(AbstractBGDungeon.rewardDeck, str(pendingOwn, "reward"));
                    decodeInto(AbstractBGDungeon.rareRewardDeck, str(pendingOwn, "rare"));
                }
                if (pendingShared != null) {
                    decodeInto(AbstractBGDungeon.colorlessRewardDeck, str(pendingShared, "colorless"));
                    decodeInto(AbstractBGDungeon.cursesRewardDeck, str(pendingShared, "curses"));
                }
                if (pendingOwn != null || pendingShared != null) MP.logger.info("restored the reward decks");
            } catch (Throwable t) {
                MP.logger.error("could not restore the reward decks", t);
            }
            pendingOwn = null;
            pendingShared = null;
        }

        private static String str(JsonObject o, String k) {
            return o.has(k) ? o.get(k).getAsString() : null;
        }
    }

    /** Client merchant: draws made while building the shop are put back (the host's stock is used). */
    public static ArrayList<AbstractCard> snapshot() {
        return new ArrayList<>(AbstractBGDungeon.colorlessRewardDeck.group);
    }

    public static void restore(ArrayList<AbstractCard> s) {
        AbstractBGDungeon.colorlessRewardDeck.group.clear();
        AbstractBGDungeon.colorlessRewardDeck.group.addAll(s);
    }
}

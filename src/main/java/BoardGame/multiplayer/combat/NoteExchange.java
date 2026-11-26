package BoardGame.multiplayer.combat;

import BoardGame.multiplayer.MP;
import BoardGame.multiplayer.Session;
import BoardGame.multiplayer.ui.ChoicePrompt;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;

import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * A Note for Yourself, [Exchange]: give a teammate a card from your deck; they give you one of theirs.
 *
 * Offering player A: picks a teammate B, then a card ("noteoffer"). The card stays in A's deck
 * until B answers ("notereply"); then A loses it and gains B's card. A may leave the event meanwhile.
 * Receiving player B: whenever an offer arrives and B is not in a fight or another screen,
 * B picks a card of their own (no cancel: the card says exchange), loses it and gains A's card.
 */
public class NoteExchange {
    private enum Mode {NONE, OFFER_PICK, OFFER_GRID, REPLY_GRID}

    private static Mode mode = Mode.NONE;
    private static int target = -1;
    private static Runnable onSent, onCancel;
    private static JsonObject answering = null;
    private static final ArrayDeque<JsonObject> offers = new ArrayDeque<>();
    /** Cards we offered and still own, waiting for the teammate's answer. */
    private static final ArrayList<AbstractCard> offered = new ArrayList<>();

    public static boolean available() {
        if (!MP.inRun || MP.playerCount() <= 1) return false;
        return !teammates().isEmpty();
    }

    /** True while the local player is choosing a teammate or a card for an exchange. */
    public static boolean busy() {
        return mode == Mode.OFFER_PICK || mode == Mode.OFFER_GRID;
    }

    private static ArrayList<MP.Player> teammates() {
        ArrayList<MP.Player> out = new ArrayList<>();
        for (MP.Player p : MP.players) if (p.id != MP.myId && p.connected && !p.dead) out.add(p);
        return out;
    }

    private static CardGroup giveable() {
        return CardGroup.getGroupWithoutBottledCards(AbstractDungeon.player.masterDeck);
    }

    /** Start an exchange from the event. onSent runs once the offer is out; onCancel if the player backs out. */
    public static void begin(Runnable sent, Runnable cancel) {
        if (mode != Mode.NONE || !available()) return;
        if (giveable().isEmpty()) {
            Session.toast("You have no card to give");
            return;
        }
        onSent = sent;
        onCancel = cancel;
        ArrayList<MP.Player> mates = teammates();
        ArrayList<String> labels = new ArrayList<>();
        for (MP.Player p : mates) labels.add(p.name);
        labels.add("Cancel");
        mode = Mode.OFFER_PICK;
        ChoicePrompt.ask("Exchange a card with:", labels, i -> {
            if (i < 0 || i >= mates.size()) {
                cancelOffer();
                return;
            }
            target = mates.get(i).id;
            mode = Mode.OFFER_GRID;
            AbstractDungeon.gridSelectScreen.open(giveable(), 1,
                    "Choose a card to give to " + Session.nameOf(target), false, false, true, false);
        });
    }

    private static void cancelOffer() {
        mode = Mode.NONE;
        target = -1;
        Runnable c = onCancel;
        onSent = onCancel = null;
        if (c != null) c.run();
    }

    private static void putCard(JsonObject o, String prefix, AbstractCard c) {
        o.addProperty(prefix + "card", c.cardID);
        o.addProperty(prefix + "upg", c.timesUpgraded);
        o.addProperty(prefix + "misc", c.misc);
        o.addProperty(prefix + "name", c.name);
    }

    private static AbstractCard readCard(JsonObject m, String prefix) {
        if (!m.has(prefix + "card")) return null;
        try {
            return CardLibrary.getCopy(m.get(prefix + "card").getAsString(),
                    m.has(prefix + "upg") ? m.get(prefix + "upg").getAsInt() : 0,
                    m.has(prefix + "misc") ? m.get(prefix + "misc").getAsInt() : 0);
        } catch (Throwable t) {
            MP.logger.error("Note exchange: unknown card " + m.get(prefix + "card"), t);
            return null;
        }
    }

    private static String cardName(JsonObject m, String prefix) {
        return m.has(prefix + "name") ? m.get(prefix + "name").getAsString() : "a card";
    }

    private static void obtain(AbstractCard c) {
        if (c == null) return;
        AbstractDungeon.effectList.add(new ShowCardAndObtainEffect(c, Settings.WIDTH / 2f, Settings.HEIGHT / 2f));
    }

    /** Remove the given card (or the first card with the same id and upgrades) from our deck. */
    private static void loseCard(AbstractCard ref, String id, int upg) {
        ArrayList<AbstractCard> deck = AbstractDungeon.player.masterDeck.group;
        AbstractCard hit = ref != null && deck.contains(ref) ? ref : null;
        if (hit == null)
            for (AbstractCard c : deck) if (c.cardID.equals(id) && c.timesUpgraded == upg) { hit = c; break; }
        if (hit == null)
            for (AbstractCard c : deck) if (c.cardID.equals(id)) { hit = c; break; }
        if (hit != null) AbstractDungeon.player.masterDeck.removeCard(hit);
    }

    public static void onMessage(String type, int from, JsonObject m) {
        if (!MP.inRun || AbstractDungeon.player == null) return;
        int to = m.has("to") ? m.get("to").getAsInt() : -1;
        if (type.equals("noteoffer")) {
            if (to == MP.myId && from != MP.myId) offers.add(m);
            else if (from != MP.myId)
                Session.toast(Session.nameOf(from) + " offers " + Session.nameOf(to) + " a card exchange");
            return;
        }
        if (!type.equals("notereply")) return;
        if (to != MP.myId) {
            if (from != MP.myId)
                Session.toast(Session.nameOf(from) + " and " + Session.nameOf(to) + " exchanged cards");
            return;
        }
        // we offered: lose our card, gain theirs
        String id = m.has("ocard") ? m.get("ocard").getAsString() : "";
        int upg = m.has("oupg") ? m.get("oupg").getAsInt() : 0;
        AbstractCard ref = null;
        for (AbstractCard c : offered)
            if (c.cardID.equals(id) && c.timesUpgraded == upg) { ref = c; break; }
        if (ref != null) offered.remove(ref);
        loseCard(ref, id, upg);
        AbstractCard got = readCard(m, "");
        obtain(got);
        Session.toast(Session.nameOf(from) + " took your " + cardName(m, "o")
                + (got != null ? " and gave you " + got.name : ""));
    }

    private static boolean canAnswerNow() {
        if (!CardCrawlGame.isInARun() || AbstractDungeon.player == null || AbstractDungeon.currMapNode == null) return false;
        AbstractRoom r = AbstractDungeon.getCurrRoom();
        if (r == null || r.phase == AbstractRoom.RoomPhase.COMBAT || MonsterSync.active()) return false;
        if (ChoicePrompt.open() || !AbstractDungeon.gridSelectScreen.selectedCards.isEmpty()) return false;
        if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP && AbstractDungeon.firstRoomChosen
                && r.phase == AbstractRoom.RoomPhase.COMPLETE) {
            // waiting on the map: close it so the answer can be chosen
            AbstractDungeon.closeCurrentScreen();
            return false;
        }
        return AbstractDungeon.screen == AbstractDungeon.CurrentScreen.NONE && !AbstractDungeon.isScreenUp;
    }

    private static void update() {
        if (!MP.inRun) {
            mode = Mode.NONE;
            offers.clear();
            offered.clear();
            return;
        }
        switch (mode) {
            case OFFER_PICK:
                if (!ChoicePrompt.open()) cancelOffer(); // prompt replaced by another one
                return;
            case OFFER_GRID:
            case REPLY_GRID: {
                ArrayList<AbstractCard> sel = AbstractDungeon.gridSelectScreen.selectedCards;
                if (!sel.isEmpty()) {
                    AbstractCard c = sel.remove(0);
                    sel.clear();
                    if (mode == Mode.OFFER_GRID) sendOffer(c);
                    else sendReply(c);
                } else if (AbstractDungeon.screen != AbstractDungeon.CurrentScreen.GRID) {
                    if (mode == Mode.OFFER_GRID) cancelOffer();
                    else { // closed without choosing: ask again later
                        offers.addFirst(answering);
                        answering = null;
                        mode = Mode.NONE;
                    }
                }
                return;
            }
            default:
                break;
        }
        if (offers.isEmpty() || !canAnswerNow()) return;
        answering = offers.poll();
        int from = answering.get("from").getAsInt();
        CardGroup mine = giveable();
        if (mine.isEmpty()) {
            sendReply(null);
            return;
        }
        mode = Mode.REPLY_GRID;
        AbstractDungeon.gridSelectScreen.open(mine, 1, Session.nameOf(from) + " gives you "
                + cardName(answering, "") + ". Choose a card to give back", false, false, false, false);
    }

    private static void sendOffer(AbstractCard c) {
        JsonObject o = MP.msg("noteoffer");
        o.addProperty("to", target);
        putCard(o, "", c);
        MP.send(o);
        offered.add(c);
        Session.toast("Offered " + c.name + " to " + Session.nameOf(target));
        mode = Mode.NONE;
        target = -1;
        Runnable s = onSent;
        onSent = onCancel = null;
        if (s != null) s.run();
    }

    /** Answer the offer being handled: give c (null: nothing to give), take their card. */
    private static void sendReply(AbstractCard c) {
        JsonObject offer = answering;
        answering = null;
        mode = Mode.NONE;
        if (offer == null) return;
        int from = offer.get("from").getAsInt();
        JsonObject o = MP.msg("notereply");
        o.addProperty("to", from);
        if (c != null) putCard(o, "", c);
        o.addProperty("ocard", offer.get("card").getAsString());
        o.addProperty("oupg", offer.has("upg") ? offer.get("upg").getAsInt() : 0);
        o.addProperty("oname", cardName(offer, ""));
        MP.send(o);
        if (c != null) AbstractDungeon.player.masterDeck.removeCard(c);
        AbstractCard got = readCard(offer, "");
        obtain(got);
        Session.toast("You gave " + (c != null ? c.name : "nothing") + " to " + Session.nameOf(from)
                + (got != null ? " for " + got.name : ""));
    }

    @SpirePatch2(clz = AbstractDungeon.class, method = "update")
    public static class Tick {
        @SpirePostfixPatch
        public static void postfix() {
            try {
                update();
            } catch (Throwable t) {
                MP.logger.error("Note exchange", t);
                mode = Mode.NONE;
            }
        }
    }
}

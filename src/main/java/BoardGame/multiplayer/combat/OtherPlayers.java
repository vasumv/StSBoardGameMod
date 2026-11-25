package BoardGame.multiplayer.combat;

import BoardGame.multiplayer.MP;
import BoardGame.multiplayer.Session;
import BoardGame.multiplayer.ui.ChoicePrompt;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;

import java.util.ArrayList;
import java.util.function.IntConsumer;

/** Card, potion and relic effects that reach other players. */
public class OtherPlayers {
    /** Last Attack another player played this turn (card id + upgraded), for Foreign Influence. */
    public static String lastOtherAttack;
    public static boolean lastOtherAttackUpgraded;
    public static String lastOtherAttackBy;

    public static boolean multi() {
        return MP.inRun && MP.playerCount() > 1 && MonsterSync.active();
    }

    public static ArrayList<MP.Player> teammates() {
        ArrayList<MP.Player> l = new ArrayList<>();
        for (MP.Player p : MP.players) if (p.id != MP.myId && p.connected && !p.dead) l.add(p);
        return l;
    }

    /** A teammate's play resolved: remember it for copy effects. */
    public static void onRemotePlay(int from, String cardId, boolean upgraded) {
        AbstractCard c = CardLibrary.getCopy(cardId, upgraded ? 1 : 0, 0);
        if (c == null) return;
        if (c.type == AbstractCard.CardType.ATTACK) {
            lastOtherAttack = cardId;
            lastOtherAttackUpgraded = upgraded;
            lastOtherAttackBy = Session.nameOf(from);
        }
        // Doppelganger copies "the last Attack or Skill played by any player"
        BoardGame.cards.BGGreen.BGDoppelganger.cardsPlayedThisTurn.add(c);
    }

    public static void newTurn() {
        lastOtherAttack = null;
    }

    /** Ask "who gets it?" (you or a teammate) before the action queue continues. */
    public static void askRecipient(String what, IntConsumer pickPlayerId) {
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Integer> ids = new ArrayList<>();
        labels.add("You");
        ids.add(MP.myId);
        for (MP.Player p : teammates()) {
            labels.add(p.name);
            ids.add(p.id);
        }
        AbstractDungeon.actionManager.addToTop(ChoicePrompt.action(what, labels, i -> pickPlayerId.accept(ids.get(i))));
    }

    /** Give Block to a player (you, or a teammate through the message stream). */
    public static void giveBlock(int playerId, int amount, String source) {
        if (playerId == MP.myId) {
            AbstractDungeon.actionManager.addToBottom(new GainBlockAction(AbstractDungeon.player, AbstractDungeon.player, amount));
            return;
        }
        JsonObject o = MP.msg("give");
        o.addProperty("to", playerId);
        o.addProperty("kind", "block");
        o.addProperty("amount", amount);
        o.addProperty("card", source);
        MP.send(o);
    }

    /** Remove Weak and Vulnerable from a teammate (or everyone with to = -2). */
    public static void cleanse(int playerId, String source) {
        JsonObject o = MP.msg("give");
        o.addProperty("to", playerId);
        o.addProperty("kind", "cleanse");
        o.addProperty("amount", 0);
        o.addProperty("card", source);
        MP.send(o);
    }

    /** "You may switch rows with another player": in a fight, applied straight away. */
    public static void offerRowSwitch(String source) {
        if (!multi()) return;
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Integer> ids = new ArrayList<>();
        labels.add("Don't switch");
        ids.add(-1);
        for (MP.Player p : teammates()) {
            labels.add("Switch with " + p.name);
            ids.add(p.id);
        }
        AbstractDungeon.actionManager.addToBottom(ChoicePrompt.action(source + ": switch rows?", labels, i -> {
            if (ids.get(i) < 0) return;
            JsonObject o = MP.msg("row");
            o.addProperty("with", ids.get(i));
            o.addProperty("now", true);
            MP.send(o);
        }));
    }
}

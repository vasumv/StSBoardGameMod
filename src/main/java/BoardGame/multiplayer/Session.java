package BoardGame.multiplayer;

import BoardGame.multiplayer.combat.MonsterSync;
import BoardGame.multiplayer.combat.RngSync;
import BoardGame.multiplayer.undo.StateSnapshot;
import BoardGame.multiplayer.undo.UndoManager;
import com.badlogic.gdx.Gdx;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardQueueItem;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapEdge;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;
import com.megacrit.cardcrawl.vfx.ThoughtBubble;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardBrieflyEffect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/**
 * In-run multiplayer logic. Every game processes the same ordered message stream:
 *
 * - play:  a player asks to play a card. Plays resolve one at a time in stream order.
 *          The owner's game plays it and answers with "done" (full enemy state);
 *          every other game applies that state. A snapshot is taken in every game
 *          before each play, so the group can roll back to before any play.
 * - delta: enemy changes that happened in one game outside a queued play (potions,
 *          end-of-turn orbs and poison, start-of-turn relics). Merged additively.
 * - ready: end-turn toggles. When all players are ready the turn ends everywhere;
 *          before enemies act, every game waits for everyone's end-of-turn delta.
 * - rb/rbv: rollback proposal and votes; needs every player to accept.
 * - vote/move: map path votes; the host announces the move once all agree.
 */
public class Session {
    // ---------- play queue ----------
    public static class Play {
        public long id;
        public int from;
        public String uuid;
        public String target;
        public String label;
        public boolean isDelta;
        public JsonObject delta;
        public int deltaSeq;
        public boolean eot;
        public int to = -1;
    }

    public static class Step {
        public final long playId;
        public final String label;
        public final StateSnapshot before;

        Step(long playId, String label, StateSnapshot before) {
            this.playId = playId;
            this.label = label;
            this.before = before;
        }
    }

    private static final ArrayDeque<Play> queue = new ArrayDeque<>();
    /** Plays requested but not yet answered with "done" (stream-ordered; identical everywhere). */
    private static final HashSet<Long> logicalPending = new HashSet<>();
    private static final HashSet<Long> cancelled = new HashSet<>();
    private static final HashMap<Long, JsonObject> results = new HashMap<>();
    public static final ArrayList<Step> steps = new ArrayList<>();
    private static Play resolving;
    private static boolean resolvingStarted;
    public static CardQueueItem authorizedItem;
    private static final HashSet<String> requestedCards = new HashSet<>();

    // deltas
    private static int myDeltaSeq = 0;
    private static final ArrayList<JsonObject> myInflight = new ArrayList<>(); // each has "seq" and "delta"
    private static final HashMap<Integer, HashSet<Integer>> includedDeltas = new HashMap<>(); // from -> seqs
    private static JsonObject baseline; // enemy state the local game last agreed on
    private static boolean wasIdle = true;

    // end of turn
    public static boolean endTurnPending = false;
    private static boolean endTurnTriggered = false;
    public static boolean authorizedEndTurn = false;
    private static JsonObject eotBaseline;
    public static boolean eotSent = false;
    private static final HashSet<Integer> eotReceived = new HashSet<>();
    private static JsonObject eotHostRng;
    public static boolean eotReleased = false;
    public static boolean sotCapturePending = false;
    private static JsonObject sotBaseline;

    // rollback
    public static class Proposal {
        public long stepPlayId;
        public String label;
        public int proposer;
        public final HashSet<Integer> yes = new HashSet<>();
    }

    public static Proposal proposal;
    private static Long rollbackTo; // approved, waiting to execute locally

    // effects a card gives "to any player": who receives them (player id); set per play
    public static int giveTarget = -1;
    /** Teammate the card being released was dropped on (-1: yourself). */
    public static int nextRecipient = -1;

    // map
    public static final HashMap<Integer, int[]> mapVotes = new HashMap<>();
    public static MapRoomNode authorizedNode;

    // tracking
    private static AbstractRoom trackedRoom;
    private static int trackedTurn = -1;
    private static boolean sentDead = false;
    /** Last Stand was used this Act: the party may not continue to the next Act. */
    public static boolean lastStandUsed = false;
    private static boolean battleEndHandled = false;
    private static String lastStatus = "";
    private static float statusTimer = 0f;
    public static final ArrayList<String> toasts = new ArrayList<>();
    public static final ArrayList<Float> toastTimes = new ArrayList<>();

    public static void reset() {
        knownRelics.clear();
        queue.clear();
        logicalPending.clear();
        cancelled.clear();
        results.clear();
        steps.clear();
        resolving = null;
        resolvingStarted = false;
        authorizedItem = null;
        requestedCards.clear();
        myDeltaSeq = 0;
        myInflight.clear();
        includedDeltas.clear();
        baseline = null;
        resetTurnState();
        proposal = null;
        rollbackTo = null;
        mapVotes.clear();
        authorizedNode = null;
        trackedRoom = null;
        trackedTurn = -1;
        sentDead = false;
        lastStatus = "";
        toasts.clear();
        toastTimes.clear();
    }

    private static void resetTurnState() {
        endTurnPending = false;
        endTurnTriggered = false;
        authorizedEndTurn = false;
        eotBaseline = null;
        eotSent = false;
        eotReceived.clear();
        eotHostRng = null;
        eotReleased = false;
        for (MP.Player p : MP.players) p.endTurnReady = false;
    }

    public static void toast(String text) {
        MP.logger.info("[toast] " + text);
        toasts.add(text);
        toastTimes.add(4f);
        while (toasts.size() > 5) {
            toasts.remove(0);
            toastTimes.remove(0);
        }
    }

    public static boolean canProcess(JsonObject m) {
        return true;
    }

    public static String nameOf(int id) {
        MP.Player p = MP.player(id);
        return p == null ? "Player " + id : p.name;
    }

    // ---------- local requests ----------

    /** Called when the local player releases a card: send it to the shared queue instead of playing it. */
    /** Sequential Turns: whose turn it is (lowest row still playing), or null when everyone plays at once. */
    public static MP.Player currentActor() {
        if (!MP.sequential) return null;
        MP.Player best = null;
        for (MP.Player p : MP.players) {
            if (!p.connected || p.dead || p.endTurnReady) continue;
            if (best == null || p.row < best.row) best = p;
        }
        return best;
    }

    public static void requestPlay(CardQueueItem item) {
        if (item.card == null) return;
        MP.Player actor = currentActor();
        if (actor != null && actor.id != MP.myId) {
            toast("It's " + actor.name + "'s turn");
            return;
        }
        if (BoardGame.multiplayer.combat.DiePhase.deciding()) {
            toast("Keep or change the die first");
            return;
        }
        String uuid = item.card.uuid.toString();
        if (requestedCards.contains(uuid)) return;
        requestedCards.add(uuid);
        JsonObject o = MP.msg("play");
        o.addProperty("uuid", uuid);
        o.addProperty("target", item.monster == null ? "" : String.valueOf(MonsterSync.id(item.monster)));
        o.addProperty("label", item.card.name + (item.monster != null ? " > " + item.monster.name : ""));
        o.addProperty("cardId", item.card.cardID);
        o.addProperty("upgraded", item.card.upgraded);
        o.addProperty("to", nextRecipient);
        if (nextRecipient >= 0) o.addProperty("label", item.card.name + " > " + nameOf(nextRecipient));
        nextRecipient = -1;
        MP.send(o);
    }

    public static boolean isRequested(AbstractCard c) {
        return c != null && requestedCards.contains(c.uuid.toString());
    }

    public static void toggleReady() {
        BoardGame.multiplayer.combat.DiePhase.confirm(); // ending the turn accepts the die
        MP.Player me = MP.me();
        MP.Player actor = currentActor();
        if (actor != null && actor.id != MP.myId && (me == null || !me.endTurnReady)) {
            toast("It's " + actor.name + "'s turn");
            return;
        }
        JsonObject o = MP.msg("ready");
        o.addProperty("v", me == null || !me.endTurnReady);
        MP.send(o);
    }

    public static void proposeRollback(int stepIndex) {
        if (stepIndex < 0 || stepIndex >= steps.size()) return;
        if (proposal != null) {
            toast("A rollback vote is already open");
            return;
        }
        JsonObject o = MP.msg("rb");
        o.addProperty("step", steps.get(stepIndex).playId);
        o.addProperty("label", steps.get(stepIndex).label);
        MP.send(o);
    }

    public static void voteRollback(boolean yes) {
        if (proposal == null) return;
        JsonObject o = MP.msg("rbv");
        o.addProperty("step", proposal.stepPlayId);
        o.addProperty("yes", yes);
        MP.send(o);
    }

    public static void voteMap(int x, int y) {
        JsonObject o = MP.msg("vote");
        o.addProperty("x", x);
        o.addProperty("y", y);
        MP.send(o);
    }

    public static void cycleGiveTarget() {
        ArrayList<Integer> ids = new ArrayList<>();
        for (MP.Player p : MP.players) if (p.connected) ids.add(p.id);
        int cur = ids.indexOf(giveTarget < 0 ? MP.myId : giveTarget);
        giveTarget = ids.get((cur + 1) % ids.size());
    }

    public static int giveTargetId() {
        return giveTarget < 0 || MP.player(giveTarget) == null ? MP.myId : giveTarget;
    }

    private static boolean givesToAnyPlayer(AbstractCard c) {
        String d = c.rawDescription == null ? "" : c.rawDescription.toLowerCase();
        return d.contains("any player");
    }

    /** For "to any player" cards aimed at a teammate: send the effect over instead of applying it here. */
    public static boolean redirectToTeammate(com.megacrit.cardcrawl.actions.AbstractGameAction a, AbstractCard card) {
        int to = giveTargetId();
        if (to == MP.myId || !givesToAnyPlayer(card)) return false;
        AbstractPlayer me = AbstractDungeon.player;
        String kind = null;
        int amount = 0;
        String power = null;
        try {
            if (a instanceof com.megacrit.cardcrawl.actions.common.GainBlockAction && a.target == me) {
                kind = "block";
                amount = a.amount;
            } else if (a instanceof com.megacrit.cardcrawl.actions.common.GainEnergyAction) {
                kind = "energy";
                amount = (Integer) basemod.ReflectionHacks.getPrivate(a,
                        com.megacrit.cardcrawl.actions.common.GainEnergyAction.class, "energyGain");
            } else if (a instanceof com.megacrit.cardcrawl.actions.common.DrawCardAction && a.target == me) {
                kind = "draw";
                amount = a.amount;
            } else if (a instanceof com.megacrit.cardcrawl.actions.common.ApplyPowerAction && a.target == me) {
                AbstractPower p = basemod.ReflectionHacks.getPrivate(a,
                        com.megacrit.cardcrawl.actions.common.ApplyPowerAction.class, "powerToApply");
                kind = "power";
                amount = a.amount;
                power = p.getClass().getName();
            }
        } catch (Throwable t) {
            MP.logger.error("could not redirect", t);
            return false;
        }
        if (kind == null) return false;
        JsonObject o = MP.msg("give");
        o.addProperty("to", to);
        o.addProperty("kind", kind);
        o.addProperty("amount", amount);
        if (power != null) o.addProperty("power", power);
        o.addProperty("card", card.name);
        MP.send(o);
        return true;
    }

    private static void receiveGive(int from, JsonObject m) {
        if (m.get("to").getAsInt() != MP.myId) return;
        AbstractPlayer p = AbstractDungeon.player;
        if (p == null || !combat()) return;
        int amount = m.get("amount").getAsInt();
        String kind = m.get("kind").getAsString();
        com.megacrit.cardcrawl.actions.GameActionManager am = AbstractDungeon.actionManager;
        switch (kind) {
            case "block":
                am.addToBottom(new com.megacrit.cardcrawl.actions.common.GainBlockAction(p, p, amount));
                break;
            case "energy":
                am.addToBottom(new com.megacrit.cardcrawl.actions.common.GainEnergyAction(amount));
                break;
            case "draw":
                am.addToBottom(new com.megacrit.cardcrawl.actions.common.DrawCardAction(p, amount));
                break;
            case "power":
                try {
                    Class<?> c = Class.forName(m.get("power").getAsString(), true, Session.class.getClassLoader());
                    for (java.lang.reflect.Constructor<?> k : c.getConstructors()) {
                        Class<?>[] ts = k.getParameterTypes();
                        if (ts.length == 2 && ts[1] == int.class) {
                            AbstractPower pw = (AbstractPower) k.newInstance(p, amount);
                            am.addToBottom(new com.megacrit.cardcrawl.actions.common.ApplyPowerAction(p, p, pw, amount));
                            break;
                        }
                    }
                } catch (Throwable t) {
                    MP.logger.error("could not apply given power", t);
                }
                break;
        }
        toast(nameOf(from) + "'s " + m.get("card").getAsString() + ": +" + amount + " " + kind);
    }

    // ---------- out of combat: potions, gold, rows ----------

    public static void givePotion(int slot, int to) {
        AbstractPlayer p = AbstractDungeon.player;
        if (combat() || slot < 0 || slot >= p.potions.size()) return;
        com.megacrit.cardcrawl.potions.AbstractPotion po = p.potions.get(slot);
        if (po instanceof com.megacrit.cardcrawl.potions.PotionSlot) return;
        p.removePotion(po);
        JsonObject o = MP.msg("potion");
        o.addProperty("to", to);
        o.addProperty("id", po.ID);
        MP.send(o);
    }

    public static void giveGold(int amount, int to) {
        AbstractPlayer p = AbstractDungeon.player;
        if (amount <= 0 || p.gold < amount) return;
        p.loseGold(amount);
        JsonObject o = MP.msg("gold");
        o.addProperty("to", to);
        o.addProperty("amount", amount);
        MP.send(o);
    }

    private static final HashMap<Integer, Integer> rowRequests = new HashMap<>();

    /** Boss relics: once a player takes one, it is gone for the others. */
    public static void claimBossRelic(String id) {
        JsonObject o = MP.msg("bossrelic");
        o.addProperty("id", id);
        MP.send(o);
    }

    public static void swapRow(int with) {
        JsonObject o = MP.msg("row");
        o.addProperty("with", with);
        MP.send(o);
    }

    // ---------- stream handling ----------

    public static void handle(String type, int from, JsonObject m) {
        long n = m.has("n") ? m.get("n").getAsLong() : -1;
        switch (type) {
            case "play": {
                Play p = new Play();
                p.id = n;
                p.from = from;
                p.uuid = m.get("uuid").getAsString();
                p.target = m.get("target").getAsString();
                p.label = nameOf(from) + ": " + m.get("label").getAsString();
                p.to = m.has("to") ? m.get("to").getAsInt() : -1;
                queue.add(p);
                logicalPending.add(n);
                MP.Player pl = MP.player(from);
                if (pl != null) pl.endTurnReady = false; // playing a card cancels "ready"
                if (proposal != null) cancelled.add(n);
                return;
            }
            case "done": {
                long id = m.get("id").getAsLong();
                logicalPending.remove(id);
                if (m.has("included")) {
                    HashSet<Integer> inc = includedDeltas.computeIfAbsent(from, k -> new HashSet<>());
                    for (com.google.gson.JsonElement e : m.getAsJsonArray("included")) inc.add(e.getAsInt());
                }
                if (!cancelled.contains(id)) results.put(id, m);
                maybeEndTurn();
                return;
            }
            case "delta": {
                Play p = new Play();
                p.id = n;
                p.from = from;
                p.isDelta = true;
                p.delta = m.getAsJsonObject("delta");
                p.deltaSeq = m.get("seq").getAsInt();
                p.eot = m.has("eot") && m.get("eot").getAsBoolean();
                if (p.eot) {
                    eotReceived.add(from);
                    if (from == 0 && m.has("rng")) eotHostRng = m.getAsJsonObject("rng");
                }
                queue.add(p);
                return;
            }
            case "ready": {
                MP.Player pl = MP.player(from);
                if (pl != null) pl.endTurnReady = m.get("v").getAsBoolean();
                maybeEndTurn();
                return;
            }
            case "status": {
                MP.Player pl = MP.player(from);
                if (pl == null) return;
                pl.hp = m.get("hp").getAsInt();
                pl.maxHp = m.get("maxHp").getAsInt();
                pl.block = m.get("block").getAsInt();
                pl.energy = m.get("energy").getAsInt();
                pl.handSize = m.get("hand").getAsInt();
                pl.powers = m.get("powers").getAsString();
                pl.gold = m.has("gold") ? m.get("gold").getAsInt() : pl.gold;
                pl.where = m.has("where") ? m.get("where").getAsString() : pl.where;
                return;
            }
            case "rb": {
                if (proposal != null) return;
                proposal = new Proposal();
                proposal.stepPlayId = m.get("step").getAsLong();
                proposal.label = m.get("label").getAsString();
                proposal.proposer = from;
                proposal.yes.add(from);
                // plays requested while the vote is open are dropped if it passes
                checkProposal();
                return;
            }
            case "rbv": {
                if (proposal == null || proposal.stepPlayId != m.get("step").getAsLong()) return;
                if (!m.get("yes").getAsBoolean()) {
                    toast(nameOf(from) + " declined the rollback");
                    proposal = null;
                    cancelled.clear();
                    return;
                }
                proposal.yes.add(from);
                checkProposal();
                return;
            }
            case "vote": {
                mapVotes.put(from, new int[]{m.get("x").getAsInt(), m.get("y").getAsInt()});
                if (MP.isHost()) maybeMove();
                return;
            }
            case "move": {
                mapVotes.clear();
                RngSync.apply(m.getAsJsonObject("rng"));
                doMove(m.get("x").getAsInt(), m.get("y").getAsInt());
                return;
            }
            case "give":
                receiveGive(from, m);
                return;
            case "deadadv":
                BoardGame.events.BGDeadAdventurer.onPartyMessage(from, m);
                return;
            case "relicgone":
                if (from != MP.myId) removeFromDecks(m.get("id").getAsString());
                return;
            case "key":
                BoardGame.multiplayer.combat.SharedRewards.onMessage(type, from, m);
                return;
            case "neowstart":
                BoardGame.neow.BGNeowEvent.onPartyStart(from, m);
                return;
            case "relicreveal":
            case "relicpick":
                BoardGame.multiplayer.ui.RelicPicker.onMessage(type, from, m);
                return;
            case "temple":
            case "fish":
                BoardGame.multiplayer.combat.SharedEvents.onMessage(type, from, m);
                return;
            case "shopstock":
            case "shopbuy":
                BoardGame.multiplayer.combat.SharedShop.onMessage(type, from, m);
                return;
            case "die":
            case "lock":
                BoardGame.multiplayer.combat.DiePhase.onMessage(type, from, m);
                return;
            case "potion": {
                if (m.get("to").getAsInt() != MP.myId) return;
                com.megacrit.cardcrawl.potions.AbstractPotion po =
                        com.megacrit.cardcrawl.helpers.PotionHelper.getPotion(m.get("id").getAsString());
                if (po == null) return;
                if (!AbstractDungeon.player.obtainPotion(po)) {
                    toast("No free potion slot for " + nameOf(from) + "'s potion; sending it back");
                    JsonObject back = MP.msg("potion");
                    back.addProperty("to", from);
                    back.addProperty("id", po.ID);
                    MP.send(back);
                } else toast(nameOf(from) + " gave you " + po.name);
                return;
            }
            case "gold": {
                if (m.get("to").getAsInt() != MP.myId) return;
                AbstractDungeon.player.gainGold(m.get("amount").getAsInt());
                toast(nameOf(from) + " paid you " + m.get("amount").getAsInt() + " gold");
                return;
            }
            case "note":
                toast(m.get("text").getAsString());
                return;
            case "bossrelic": {
                if (from == MP.myId) return;
                String id = m.get("id").getAsString();
                if (AbstractDungeon.bossRelicScreen != null && AbstractDungeon.bossRelicScreen.relics != null)
                    AbstractDungeon.bossRelicScreen.relics.removeIf(r -> r.relicId.equals(id));
                toast(nameOf(from) + " took " + id.replace("BoardGame:", ""));
                return;
            }
            case "row": {
                if (combat()) return;
                MP.Player a = MP.player(from), b = MP.player(m.get("with").getAsInt());
                if (a == null || b == null) return;
                rowRequests.put(a.id, b.id);
                Integer back = rowRequests.get(b.id);
                if (back == null || back != a.id) {
                    toast(a.name + " wants to switch rows with " + b.name);
                    return;
                }
                rowRequests.remove(a.id);
                rowRequests.remove(b.id);
                int r = a.row;
                a.row = b.row;
                b.row = r;
                toast(a.name + " and " + b.name + " switched rows");
                return;
            }
            case "dead": {
                boolean bossFight = m.has("boss") && m.get("boss").getAsBoolean();
                MP.Player fallen = MP.player(from);
                if (fallen != null) fallen.dead = true;
                if (MP.lastStand && bossFight) {
                    // Last Stand: the others fight on; this party can't go on to the next Act
                    lastStandUsed = true;
                    if (fallen != null) fallen.endTurnReady = true;
                    toast(nameOf(from) + " has fallen. Last Stand: the rest of the party fights on!");
                    boolean anyAlive = false;
                    for (MP.Player q : MP.players) anyAlive |= q.connected && !q.dead;
                    if (anyAlive) {
                        maybeEndTurn();
                        return;
                    }
                }
                toast(nameOf(from) + " has died. The party is defeated.");
                AbstractPlayer p = AbstractDungeon.player;
                if (p != null && !p.isDead && CardCrawlGame.isInARun() && from != MP.myId) {
                    sentDead = true;
                    p.currentHealth = 0;
                    p.isDead = true;
                    AbstractDungeon.deathScreen = new com.megacrit.cardcrawl.screens.DeathScreen(
                            AbstractDungeon.getMonsters());
                }
                return;
            }
            default:
                MP.logger.info("unhandled message " + m);
        }
    }

    private static boolean allConnectedIn(HashSet<Integer> ids) {
        for (MP.Player p : MP.players) if (p.connected && !p.dead && !ids.contains(p.id)) return false;
        return true;
    }

    private static void checkProposal() {
        if (proposal == null) return;
        if (!allConnectedIn(proposal.yes)) return;
        // approved: everything not yet answered is dropped
        cancelled.addAll(logicalPending);
        logicalPending.clear();
        rollbackTo = proposal.stepPlayId;
        toast("Rolling back to before " + proposal.label);
        proposal = null;
        for (MP.Player p : MP.players) p.endTurnReady = false;
    }

    private static void maybeEndTurn() {
        if (endTurnPending || !logicalPending.isEmpty() || proposal != null) return;
        for (MP.Player p : MP.players) if (p.connected && !p.dead && !p.endTurnReady) return;
        endTurnPending = true;
        for (MP.Player p : MP.players) p.endTurnReady = false;
    }

    private static void maybeMove() {
        HashMap<String, Integer> count = new HashMap<>();
        HashMap<String, int[]> byKey = new HashMap<>();
        for (MP.Player p : MP.players) {
            if (!p.connected) continue;
            int[] v = mapVotes.get(p.id);
            if (v == null) return; // wait for everyone
            String k = v[0] + "," + v[1];
            count.merge(k, 1, Integer::sum);
            byKey.put(k, v);
        }
        if (count.isEmpty()) return;
        int best = 0;
        ArrayList<String> tied = new ArrayList<>();
        for (java.util.Map.Entry<String, Integer> e : count.entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                tied.clear();
            }
            if (e.getValue() == best) tied.add(e.getKey());
        }
        java.util.Collections.sort(tied);
        int pick = tied.size() == 1 ? 0 : new java.util.Random(MP.seed * 31 + AbstractDungeon.floorNum).nextInt(tied.size());
        if (tied.size() > 1) {
            JsonObject t = MP.msg("note");
            t.addProperty("text", "Tied vote: the die picked a room");
            MP.send(t);
        }
        int[] first = byKey.get(tied.get(pick));
        JsonObject o = MP.msg("move");
        o.addProperty("x", first[0]);
        o.addProperty("y", first[1]);
        o.add("rng", RngSync.capture(RngSync.ALL));
        MP.send(o);
    }

    /** Move the party: replay the game's own map click for that room. */
    private static void doMove(int x, int y) {
        pendingMove = new int[]{x, y};
    }

    private static int[] pendingMove;

    /** Same steps the game takes when the boss icon is clicked. */
    private static void enterBoss() {
        AbstractDungeon.getCurrMapNode().taken = true;
        for (MapEdge e : AbstractDungeon.getCurrMapNode().getEdges()) if (e != null) e.markAsTaken();
        CardCrawlGame.music.fadeOutTempBGM();
        MapRoomNode node = new MapRoomNode(-1, 15);
        node.room = new com.megacrit.cardcrawl.rooms.MonsterRoomBoss();
        AbstractDungeon.nextRoom = node;
        if (AbstractDungeon.pathY.size() > 1) {
            AbstractDungeon.pathX.add(AbstractDungeon.pathX.get(AbstractDungeon.pathX.size() - 1));
            AbstractDungeon.pathY.add(AbstractDungeon.pathY.get(AbstractDungeon.pathY.size() - 1) + 1);
        } else {
            AbstractDungeon.pathX.add(1);
            AbstractDungeon.pathY.add(15);
        }
        AbstractDungeon.nextRoomTransitionStart();
        AbstractDungeon.dungeonMapScreen.map.bossHb.hovered = false;
    }

    private static void updatePendingMove() {
        if (pendingMove == null) return;
        try {
            if (AbstractDungeon.screen != AbstractDungeon.CurrentScreen.MAP) {
                if (AbstractDungeon.isScreenUp) AbstractDungeon.closeCurrentScreen();
                AbstractDungeon.dungeonMapScreen.open(false);
                return; // let the map open first
            }
            boolean boss = pendingMove[1] >= AbstractDungeon.map.size()
                    || AbstractDungeon.map.get(pendingMove[1]).get(pendingMove[0]).room
                    instanceof com.megacrit.cardcrawl.rooms.MonsterRoomBoss;
            if (boss) {
                pendingMove = null;
                enterBoss();
                return;
            }
            MapRoomNode node = AbstractDungeon.map.get(pendingMove[1]).get(pendingMove[0]);
            if (AbstractDungeon.screen != AbstractDungeon.CurrentScreen.MAP) {
                if (AbstractDungeon.isScreenUp) AbstractDungeon.closeCurrentScreen();
                AbstractDungeon.dungeonMapScreen.open(false);
                return; // let the map open first
            }
            authorizedNode = node;
            if (!AbstractDungeon.firstRoomChosen) {
                AbstractDungeon.firstRoomChosen = true;
                AbstractDungeon.dungeonMapScreen.dismissable = true;
            }
            basemod.ReflectionHacks.setPrivate(node, MapRoomNode.class, "animWaitTimer", 0.25f);
        } catch (Throwable t) {
            MP.logger.error("could not move", t);
        }
        pendingMove = null;
    }

    private static final HashSet<String> knownRelics = new HashSet<>();

    /** A relic someone obtains leaves the shared relic decks in every game (one physical copy). */
    private static void shareObtainedRelics() {
        AbstractPlayer p = AbstractDungeon.player;
        if (p == null || !CardCrawlGame.isInARun()) return;
        for (com.megacrit.cardcrawl.relics.AbstractRelic r : p.relics) {
            if (knownRelics.add(r.relicId)) {
                removeFromDecks(r.relicId);
                JsonObject o = MP.msg("relicgone");
                o.addProperty("id", r.relicId);
                MP.send(o);
            }
        }
    }

    private static void removeFromDecks(String id) {
        java.util.ArrayList<com.megacrit.cardcrawl.relics.AbstractRelic> d = BoardGame.relics.AbstractBGRelic.relicDeck;
        if (d != null) d.removeIf(r -> r.relicId.equals(id));
        java.util.ArrayList<com.megacrit.cardcrawl.relics.AbstractRelic> b = BoardGame.relics.AbstractBGRelic.bossRelicDeck;
        if (b != null) b.removeIf(r -> r.relicId.equals(id));
    }

    private static Object rotatedRelicDeck, rotatedPotionDeck;

    /**
     * Relic and potion decks are shared in the board game. Every game holds the same shuffled
     * copy, so each player starts drawing at a different point of it to avoid duplicates.
     */
    private static void offsetSharedDecks() {
        int n = MP.playerCount(), row = MP.myRow();
        if (n < 2) return;
        java.util.ArrayList<com.megacrit.cardcrawl.relics.AbstractRelic> rd = BoardGame.relics.AbstractBGRelic.relicDeck;
        if (rd != null && rd != rotatedRelicDeck && rd.size() > 0) {
            java.util.Collections.rotate(rd, -(row * rd.size() / n));
            rotatedRelicDeck = rd;
        }
        java.util.ArrayList<String> pd = BoardGame.potions.PotionHelperPatch.potionDeck;
        if (pd != null && pd != rotatedPotionDeck && pd.size() > 0) {
            java.util.Collections.rotate(pd, -(row * pd.size() / n));
            rotatedPotionDeck = pd;
        }
    }

    // ---------- per-frame ----------

    private static boolean combat() {
        return MonsterSync.active();
    }

    public static void update() {
        if (!MP.inRun) return;
        float dt = Gdx.graphics.getDeltaTime();
        for (int i = toastTimes.size() - 1; i >= 0; i--) {
            toastTimes.set(i, toastTimes.get(i) - dt);
            if (toastTimes.get(i) <= 0) {
                toastTimes.remove(i);
                toasts.remove(i);
            }
        }
        sendStatus(dt);
        checkDeath();
        BoardGame.neow.BGNeowEvent.tryPendingStart();
        updatePendingMove();
        offsetSharedDecks();
        shareObtainedRelics();
        if (!CardCrawlGame.isInARun() || AbstractDungeon.currMapNode == null) return;

        AbstractRoom room = AbstractDungeon.getCurrRoom();
        if (room != trackedRoom) {
            trackedRoom = room;
            onRoomChanged();
        }
        if (!combat()) {
            // the local game may have just won the fight: share what ended it
            if (room != null && room.monsters != null && room.isBattleOver && !battleEndHandled && baseline != null
                    && AbstractDungeon.actionManager.actions.isEmpty()) {
                battleEndHandled = true;
                if (resolving != null && resolving.from == MP.myId && resolvingStarted) finishOwnPlay(true);
                else sendLocalDelta(false);
            }
            return;
        }
        if (GameActionManager.turn != trackedTurn) {
            trackedTurn = GameActionManager.turn;
            steps.clear();
            requestedCards.clear();
        }

        boolean idle = UndoManager.isIdle();

        // 1. approved rollback
        if (rollbackTo != null) {
            if (resolving != null && resolving.from == MP.myId && resolvingStarted && !idle) return;
            if (!idle && resolving == null) return;
            executeRollback(rollbackTo);
            rollbackTo = null;
            return;
        }

        // 2. finish the play we are resolving
        if (resolving != null) {
            if (resolving.from == MP.myId) {
                boolean settled = idle || (AbstractDungeon.getCurrRoom().isBattleOver
                        && AbstractDungeon.actionManager.actions.isEmpty());
                if (resolvingStarted && settled && !AbstractDungeon.actionManager.cardQueue.contains(authorizedItem)) {
                    finishOwnPlay(true);
                }
            } else {
                JsonObject r = results.remove(resolving.id);
                if (r != null) {
                    if (r.get("ok").getAsBoolean()) {
                        RngSync.apply(r.getAsJsonObject("rng"));
                        MonsterSync.applyFull(r.getAsJsonObject("state"), true);
                        if (r.has("die") && !BoardGame.multiplayer.combat.DiePhase.deciding())
                            BoardGame.thedie.TheDie.monsterRoll = r.get("die").getAsInt();
                        reapplyInflight();
                        showRemoteCard(r);
                    } else {
                        dropStep(resolving.id);
                    }
                    baseline = MonsterSync.capture();
                    resolving = null;
                }
            }
            return;
        }

        // 3. end of turn
        if (endTurnPending && !endTurnTriggered && idle && queue.isEmpty()) {
            endTurnTriggered = true;
            eotBaseline = MonsterSync.capture();
            BoardGame.multiplayer.combat.MPHooks.hostPoisonTick();
            authorizedEndTurn = true;
            AbstractDungeon.overlayMenu.endTurnButton.disable(true);
            authorizedEndTurn = false;
            return;
        }

        // 4. local changes made outside the queue (potions, start-of-turn effects...)
        if (idle && !wasIdle && !endTurnTriggered) sendLocalDelta(false);
        wasIdle = idle;

        // 5. next queued item
        if (!idle || proposal != null || queue.isEmpty()) return;
        Play p = queue.poll();
        if (p.isDelta) {
            if (p.from == MP.myId) {
                myInflight.removeIf(o -> o.get("seq").getAsInt() == p.deltaSeq);
            } else {
                HashSet<Integer> inc = includedDeltas.get(p.from);
                if (inc == null || !inc.remove(p.deltaSeq)) {
                    MonsterSync.applyDelta(p.delta, true);
                    reapplyNothing();
                }
            }
            baseline = MonsterSync.capture();
            return;
        }
        if (cancelled.contains(p.id)) {
            if (p.from == MP.myId) requestedCards.remove(p.uuid);
            return;
        }
        steps.add(new Step(p.id, p.label, StateSnapshot.capture()));
        resolving = p;
        resolvingStarted = false;
        if (p.from == MP.myId) startOwnPlay(p);
    }

    private static void reapplyNothing() {
    }

    private static void startOwnPlay(Play p) {
        AbstractPlayer pl = AbstractDungeon.player;
        AbstractCard card = null;
        for (AbstractCard c : pl.hand.group) if (c.uuid.toString().equals(p.uuid)) card = c;
        AbstractMonster target = p.target.isEmpty() ? null : MonsterSync.byId(p.target);
        requestedCards.remove(p.uuid);
        if (card == null || !card.canUse(pl, target)
                || (target != null && target.isDeadOrEscaped() && card.target == AbstractCard.CardTarget.ENEMY)) {
            if (card != null) {
                AbstractDungeon.effectList.add(new ThoughtBubble(pl.dialogX, pl.dialogY, 3f,
                        card.cantUseMessage == null ? "I can't play that now." : card.cantUseMessage, true));
            }
            finishOwnPlay(false);
            return;
        }
        giveTarget = p.to;
        authorizedItem = new CardQueueItem(card, target);
        AbstractDungeon.actionManager.cardQueue.add(authorizedItem);
        resolvingStarted = true;
    }

    private static void finishOwnPlay(boolean ok) {
        JsonObject o = MP.msg("done");
        o.addProperty("id", resolving.id);
        o.addProperty("ok", ok);
        if (ok) {
            o.add("state", MonsterSync.capture());
            o.add("rng", RngSync.capture(RngSync.COMBAT));
            o.addProperty("die", BoardGame.thedie.TheDie.monsterRoll);
            com.google.gson.JsonArray inc = new com.google.gson.JsonArray();
            for (JsonObject d : myInflight) inc.add(new com.google.gson.JsonPrimitive(d.get("seq").getAsInt()));
            o.add("included", inc);
            AbstractCard c = authorizedItem == null ? null : authorizedItem.card;
            if (c != null) {
                o.addProperty("cardId", c.cardID);
                o.addProperty("upgraded", c.upgraded);
            }
        } else {
            dropStep(resolving.id);
        }
        MP.send(o);
        baseline = MonsterSync.capture();
        giveTarget = -1;
        authorizedItem = null;
        resolving = null;
        resolvingStarted = false;
    }

    private static void dropStep(long id) {
        steps.removeIf(s -> s.playId == id);
    }

    private static void showRemoteCard(JsonObject r) {
        try {
            if (!r.has("cardId")) return;
            AbstractCard c = com.megacrit.cardcrawl.helpers.CardLibrary.getCopy(r.get("cardId").getAsString(),
                    r.get("upgraded").getAsBoolean() ? 1 : 0, 0);
            if (c != null) AbstractDungeon.effectList.add(new ShowCardBrieflyEffect(c));
        } catch (Throwable ignored) {
        }
    }

    private static void reapplyInflight() {
        for (JsonObject d : myInflight) MonsterSync.applyDelta(d.getAsJsonObject("delta"), false);
    }

    /** Enemy changes caused only in this game during the enemy turn. */
    public static void shareReaction(JsonObject d) {
        if (MonsterSync.isEmpty(d)) return;
        JsonObject o = MP.msg("delta");
        int seq = myDeltaSeq++;
        o.addProperty("seq", seq);
        o.add("delta", d);
        JsonObject rec = new JsonObject();
        rec.addProperty("seq", seq);
        rec.add("delta", d);
        myInflight.add(rec);
        MP.send(o);
    }

    private static void sendLocalDelta(boolean eot) {
        if (baseline == null) {
            baseline = MonsterSync.capture();
            if (!eot) return;
        }
        JsonObject now = MonsterSync.capture();
        JsonObject d = MonsterSync.diff(eot && eotBaseline != null ? eotBaseline : baseline, now);
        baseline = now;
        if (!eot && MonsterSync.isEmpty(d)) return;
        JsonObject o = MP.msg("delta");
        int seq = myDeltaSeq++;
        o.addProperty("seq", seq);
        o.add("delta", d);
        if (eot) {
            o.addProperty("eot", true);
            if (MP.isHost()) o.add("rng", RngSync.capture(RngSync.COMBAT));
        }
        JsonObject rec = new JsonObject();
        rec.addProperty("seq", seq);
        rec.add("delta", d);
        myInflight.add(rec);
        MP.send(o);
    }

    // ---------- hooks called from patches ----------

    /** Before enemies start their turn: share end-of-turn effects and wait for everyone's. Returns true to hold. */
    public static boolean holdBeforeEnemyTurn() {
        if (!combat() || eotReleased) return false;
        if (!eotSent) {
            sendLocalDelta(true);
            eotSent = true;
        }
        // merge as they arrive (they sit in the queue)
        while (!queue.isEmpty() && queue.peek().isDelta) {
            Play p = queue.poll();
            if (p.from == MP.myId) myInflight.removeIf(o -> o.get("seq").getAsInt() == p.deltaSeq);
            else {
                HashSet<Integer> inc = includedDeltas.get(p.from);
                if (inc == null || !inc.remove(p.deltaSeq)) MonsterSync.applyDelta(p.delta, true);
            }
        }
        for (MP.Player p : MP.players) if (p.connected && !p.dead && !eotReceived.contains(p.id)) return true;
        if (!queue.isEmpty() && queue.peek().isDelta) return true;
        if (eotHostRng != null) RngSync.apply(eotHostRng);
        eotReleased = true;
        BoardGame.multiplayer.combat.MPHooks.enemyTurn = true;
        return false;
    }

    /** Enemy turn finished (before the player's start-of-turn effects): new agreed baseline. */
    public static void onEnemyTurnDone() {
        BoardGame.multiplayer.combat.MPHooks.enemyTurn = false;
        if (!combat()) return;
        resetTurnState();
        baseline = MonsterSync.capture();
        wasIdle = false; // the start-of-turn changes are sent when the game next settles
    }

    private static void onRoomChanged() {
        battleEndHandled = false;
        SavedSession.write();
        BoardGame.multiplayer.combat.DiePhase.reset();
        queue.clear();
        logicalPending.clear();
        cancelled.clear();
        results.clear();
        steps.clear();
        resolving = null;
        authorizedItem = null;
        requestedCards.clear();
        myInflight.clear();
        includedDeltas.clear();
        baseline = null;
        resetTurnState();
        proposal = null;
        rollbackTo = null;
        mapVotes.clear();
        trackedTurn = -1;
        wasIdle = false;
        BoardGame.multiplayer.combat.MPHooks.disposeDeferred();
    }

    private static void executeRollback(long playId) {
        queue.removeIf(p -> !p.isDelta);
        results.clear();
        if (resolving != null && resolving.from == MP.myId) {
            authorizedItem = null;
        }
        resolving = null;
        resolvingStarted = false;
        requestedCards.clear();
        int idx = -1;
        for (int i = 0; i < steps.size(); i++) if (steps.get(i).playId == playId) idx = i;
        if (idx >= 0) {
            Step s = steps.get(idx);
            while (steps.size() > idx) steps.remove(steps.size() - 1);
            UndoManager.restore(s.before, "Rolled back to before " + s.label);
        }
        myInflight.clear();
        includedDeltas.clear();
        baseline = MonsterSync.capture();
        cancelled.clear();
    }

    private static void sendStatus(float dt) {
        statusTimer -= dt;
        if (statusTimer > 0) return;
        statusTimer = 0.25f;
        AbstractPlayer p = AbstractDungeon.player;
        if (p == null || !CardCrawlGame.isInARun()) return;
        StringBuilder pw = new StringBuilder();
        for (AbstractPower x : p.powers) {
            if (x.name == null || x.name.isEmpty()) continue;
            pw.append(x.name).append(x.amount != 0 && x.amount != -1 ? " " + x.amount : "").append(", ");
        }
        String where = AbstractDungeon.getCurrRoom() == null ? "" : AbstractDungeon.getCurrRoom().getClass().getSimpleName();
        String s = p.currentHealth + "/" + p.maxHealth + "/" + p.currentBlock + "/" + EnergyPanel.totalCount + "/"
                + p.hand.size() + "/" + pw + "/" + p.gold + "/" + where;
        if (s.equals(lastStatus)) return;
        lastStatus = s;
        JsonObject o = MP.msg("status");
        o.addProperty("hp", p.currentHealth);
        o.addProperty("maxHp", p.maxHealth);
        o.addProperty("block", p.currentBlock);
        o.addProperty("energy", EnergyPanel.totalCount);
        o.addProperty("hand", p.hand.size());
        o.addProperty("powers", pw.toString());
        o.addProperty("gold", p.gold);
        o.addProperty("where", where);
        MP.send(o);
    }

    private static void checkDeath() {
        AbstractPlayer p = AbstractDungeon.player;
        if (sentDead || p == null || !CardCrawlGame.isInARun()) return;
        if (p.isDead || p.currentHealth <= 0 && AbstractDungeon.deathScreen != null) {
            sentDead = true;
            SavedSession.clear();
            JsonObject d = MP.msg("dead");
            d.addProperty("boss", AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.MonsterRoomBoss);
            MP.send(d);
        }
    }

    // ---------- views for UI / debug ----------

    public static String status() {
        StringBuilder sb = new StringBuilder("queue=" + queue.size() + " pending=" + logicalPending.size()
                + " resolving=" + (resolving == null ? "-" : resolving.label) + " steps=" + steps.size()
                + " endTurnPending=" + endTurnPending + " eotReleased=" + eotReleased + " inflight="
                + myInflight.size());
        if (proposal != null) sb.append(" proposal=").append(proposal.label).append(" yes=").append(proposal.yes);
        if (!mapVotes.isEmpty()) {
            sb.append(" votes=");
            for (java.util.Map.Entry<Integer, int[]> e : mapVotes.entrySet())
                sb.append(e.getKey()).append(':').append(e.getValue()[0]).append(',').append(e.getValue()[1]).append(' ');
        }
        return sb.toString();
    }

    public static String resolvingLabel() {
        return resolving == null ? null : resolving.label;
    }

    public static ArrayList<String> queuedLabels() {
        ArrayList<String> l = new ArrayList<>();
        for (Iterator<Play> it = queue.iterator(); it.hasNext(); ) {
            Play p = it.next();
            if (!p.isDelta && !cancelled.contains(p.id)) l.add(p.label);
        }
        return l;
    }
}

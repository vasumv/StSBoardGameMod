package BoardGame.multiplayer.debug;

import BoardGame.multiplayer.undo.StateSnapshot;
import BoardGame.multiplayer.undo.UndoManager;
import BoardGame.thedie.TheDie;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.cards.CardQueueItem;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.relics.AbstractRelic;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Test-only command channel, active when the JVM is started with -Dbg.debug.dir=DIR.
 * Each frame, if DIR/cmd exists it is read and deleted, each line is run as a command,
 * and replies are appended to DIR/out. Commands: state, undo, reset, play I [M], end,
 * stats, idle.
 */
public class DebugChannel {
    private static final String DIR = System.getProperty("bg.debug.dir");

    public static void poll() {
        if (DIR == null) return;
        File cmd = new File(DIR, "cmd");
        if (!cmd.exists()) return;
        try {
            List<String> lines = Files.readAllLines(cmd.toPath(), StandardCharsets.UTF_8);
            Files.delete(cmd.toPath());
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String reply;
                try {
                    reply = run(line.split("\\s+"));
                } catch (Throwable t) {
                    reply = "ERROR " + t;
                }
                Files.write(new File(DIR, "out").toPath(), ("> " + line + "\n" + reply + "\n").getBytes(
                        StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (Throwable t) {
            UndoManager.logger.error("debug channel", t);
        }
    }

    private static String run(String[] a) {
        switch (a[0]) {
            case "intents":
                return BoardGame.multiplayer.combat.IntentDetails.dumpAll();
            case "rule": { // test (host, in the lobby): "rule laststand on", "rule chooserelic on", "rule sequential off"
                boolean on = a.length > 2 && a[2].equals("on");
                switch (a[1]) {
                    case "laststand": BoardGame.multiplayer.MP.setLastStand(on); break;
                    case "chooserelic": BoardGame.multiplayer.MP.setChooseRelic(on); break;
                    case "sequential": BoardGame.multiplayer.MP.setSequential(on); break;
                    default: return "unknown rule";
                }
                return "rule " + a[1] + " " + on;
            }
            case "hurt": // test: lose HP, e.g. "hurt 5"
                AbstractDungeon.actionManager.addToBottom(new com.megacrit.cardcrawl.actions.common.LoseHPAction(
                        AbstractDungeon.player, AbstractDungeon.player, Integer.parseInt(a[1])));
                return "hurt " + a[1];
            case "deck": // test: the master deck
                return "deck=" + cards(AbstractDungeon.player.masterDeck);
            case "state":
                return state();
            case "idle":
                return "idle=" + UndoManager.isIdle() + " canUndo=" + UndoManager.canUndo() + " depth="
                        + UndoManager.undoDepth();
            case "undo":
                return "undo=" + UndoManager.undo();
            case "mp": {
                if (a.length < 2 || a[1].equals("status")) return BoardGame.multiplayer.MP.status();
                switch (a[1]) {
                    case "host":
                        return "host=" + BoardGame.multiplayer.MP.host(Integer.parseInt(a[2]));
                    case "join":
                        return "join=" + BoardGame.multiplayer.MP.join(a[2], Integer.parseInt(a[3]));
                    case "char":
                        BoardGame.multiplayer.MP.setCharacter(Integer.parseInt(a[2]));
                        return "ok";
                    case "drop": // test: cut the connection to the host
                        BoardGame.multiplayer.net.Net.testDrop();
                        return "dropped";
                    case "continue":
                        BoardGame.multiplayer.SavedSession.hostContinue();
                        return "asked";
                    case "keep":
                        BoardGame.multiplayer.combat.DiePhase.confirm();
                        return "ok";
                    case "setdie":
                        BoardGame.thedie.TheDie.tentativeRoll(Integer.parseInt(a[2]));
                        return "ok";
                    case "die":
                        return "key=" + BoardGame.multiplayer.combat.DiePhase.roundKey + " deciding="
                                + BoardGame.multiplayer.combat.DiePhase.deciding() + " decided="
                                + BoardGame.multiplayer.combat.DiePhase.decided + " me="
                                + BoardGame.multiplayer.combat.DiePhase.iConfirmed() + " roll="
                                + BoardGame.thedie.TheDie.initialRoll + "/" + BoardGame.thedie.TheDie.finalRelicRoll
                                + "/" + BoardGame.thedie.TheDie.monsterRoll;
                    case "ready":
                        BoardGame.multiplayer.Session.toggleReady();
                        return "ok";
                    case "vote":
                        BoardGame.multiplayer.Session.voteMap(Integer.parseInt(a[2]), Integer.parseInt(a[3]));
                        return "ok";
                    case "rb":
                        BoardGame.multiplayer.Session.proposeRollback(Integer.parseInt(a[2]));
                        return "ok";
                    case "yes":
                        BoardGame.multiplayer.Session.voteRollback(true);
                        return "ok";
                    case "no":
                        BoardGame.multiplayer.Session.voteRollback(false);
                        return "ok";
                    case "steps": {
                        StringBuilder sb = new StringBuilder();
                        for (BoardGame.multiplayer.Session.Step st : BoardGame.multiplayer.Session.steps)
                            sb.append('[').append(st.label).append("] ");
                        return sb.toString();
                    }
                    case "map": {
                        StringBuilder sb = new StringBuilder("screen=" + AbstractDungeon.screen + " firstChosen="
                                + AbstractDungeon.firstRoomChosen + " cur=" + (AbstractDungeon.currMapNode == null ? "-"
                                : AbstractDungeon.currMapNode.x + "," + AbstractDungeon.currMapNode.y) + " phase="
                                + (AbstractDungeon.currMapNode == null ? "-" : AbstractDungeon.getCurrRoom().phase) + " next:");
                        for (java.util.ArrayList<com.megacrit.cardcrawl.map.MapRoomNode> row : AbstractDungeon.map)
                            for (com.megacrit.cardcrawl.map.MapRoomNode n : row) {
                                if (n.room == null) continue;
                                boolean ok = AbstractDungeon.firstRoomChosen ? AbstractDungeon.currMapNode.isConnectedTo(n) : n.y == 0;
                                if (ok) sb.append(' ').append(n.x).append(',').append(n.y).append('=')
                                        .append(n.room.getClass().getSimpleName());
                            }
                        return sb.toString();
                    }
                    case "start":
                        BoardGame.multiplayer.MP.startRun(a.length > 2 ? Long.parseLong(a[2]) : System.nanoTime());
                        return "starting";
                    default:
                        return "unknown mp command";
                }
            }
            case "panel":
                return BoardGame.multiplayer.undo.HistoryPanel.debug();
            case "rollback":
                return "rollback=" + UndoManager.rollbackTo(Integer.parseInt(a[1]));
            case "history": {
                StringBuilder sb = new StringBuilder();
                for (UndoManager.Step st : UndoManager.steps()) sb.append('[').append(st.label).append("] ");
                return sb.toString();
            }
            case "reset":
                return "reset=" + UndoManager.resetTurn();
            case "stats": {
                StateSnapshot s = UndoManager.currentSnapshot();
                return s == null ? "no snapshot" : s.stats(40);
            }
            case "play": {
                if (!UndoManager.isIdle()) return "not idle";
                AbstractCard c = AbstractDungeon.player.hand.group.get(Integer.parseInt(a[1]));
                AbstractMonster m = a.length > 2
                        ? AbstractDungeon.getCurrRoom().monsters.monsters.get(Integer.parseInt(a[2])) : null;
                if (!c.canUse(AbstractDungeon.player, m)) return "cannot use " + c.cardID;
                AbstractDungeon.actionManager.cardQueue.add(new CardQueueItem(c, m));
                return "queued " + c.cardID;
            }
            case "am": {
                com.megacrit.cardcrawl.actions.GameActionManager am = AbstractDungeon.actionManager;
                StringBuilder sb = new StringBuilder("phase=" + am.phase + " cur=" + (am.currentAction == null ? "-"
                        : am.currentAction.getClass().getName()) + " turnHasEnded=" + am.turnHasEnded + " usingCard="
                        + am.usingCard + " monsterAttacksQueued=" + am.monsterAttacksQueued + " battleOver=" + AbstractDungeon.getCurrRoom().isBattleOver + " basicallyDead=" + AbstractDungeon.getCurrRoom().monsters.areMonstersBasicallyDead() + " screenUp="
                        + AbstractDungeon.isScreenUp + " screen=" + AbstractDungeon.screen + " endTurnEnabled="
                        + AbstractDungeon.overlayMenu.endTurnButton.enabled + " actions=");
                for (com.megacrit.cardcrawl.actions.AbstractGameAction x : am.actions) sb.append(x.getClass().getSimpleName()).append(' ');
                sb.append(" cardQueue=").append(am.cardQueue.size()).append(" monsterQueue=").append(am.monsterQueue.size());
                return sb.toString();
            }
            case "hit": {
                // test helper: damage an enemy outside the play queue (like a potion would)
                AbstractMonster m = AbstractDungeon.getCurrRoom().monsters.monsters.get(Integer.parseInt(a[1]));
                AbstractDungeon.actionManager.addToBottom(new com.megacrit.cardcrawl.actions.common.DamageAction(m,
                        new com.megacrit.cardcrawl.cards.DamageInfo(AbstractDungeon.player, Integer.parseInt(a[2]),
                                com.megacrit.cardcrawl.cards.DamageInfo.DamageType.THORNS)));
                return "ok";
            }
            case "proceed": {
                // test helper: what the Proceed button does after a boss and at the boss chest
                com.megacrit.cardcrawl.ui.buttons.ProceedButton pb = AbstractDungeon.overlayMenu.proceedButton;
                try {
                    if (AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.TreasureRoomBoss) {
                        java.lang.reflect.Method m = pb.getClass().getDeclaredMethod("goToNextDungeon",
                                com.megacrit.cardcrawl.rooms.AbstractRoom.class);
                        m.setAccessible(true);
                        m.invoke(pb, AbstractDungeon.getCurrRoom());
                        return "next dungeon";
                    }
                    if (AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.MonsterRoomBoss
                            && "TheBeyond".equals(AbstractDungeon.id)) { // what the real button does after Act 3
                        java.lang.reflect.Method v = pb.getClass().getDeclaredMethod("goToVictoryRoomOrTheDoor");
                        v.setAccessible(true);
                        v.invoke(pb);
                        return "victory";
                    }
                    java.lang.reflect.Method m = pb.getClass().getDeclaredMethod("goToTreasureRoom");
                    m.setAccessible(true);
                    m.invoke(pb);
                    return "boss chest";
                } catch (Throwable t) {
                    return "ERROR " + t;
                }
            }
            case "upg": {
                AbstractCard c = AbstractDungeon.player.hand.group.get(Integer.parseInt(a[1]));
                c.upgrade();
                return "upgraded " + c.cardID + " -> " + c.rawDescription;
            }
            case "skiproom":
                // test helper: finish the current non-combat room and open the map
                if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP) return "already on the map";
                if (AbstractDungeon.isScreenUp) AbstractDungeon.closeCurrentScreen();
                AbstractDungeon.getCurrRoom().phase = com.megacrit.cardcrawl.rooms.AbstractRoom.RoomPhase.COMPLETE;
                AbstractDungeon.dungeonMapScreen.open(false);
                return "ok";
            case "nextenc": { // test: the next fights use these encounters (one per row), e.g. "nextenc BoardGame:Angry Gremlin Team"
                String rest = String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length));
                String[] keys = rest.split(",");
                for (int i = keys.length - 1; i >= 0; i--) {
                    AbstractDungeon.monsterList.add(0, keys[i].trim());
                    AbstractDungeon.eliteMonsterList.add(0, keys[i].trim());
                }
                return "next encounters " + AbstractDungeon.monsterList.subList(0, Math.min(4, AbstractDungeon.monsterList.size()));
            }
            case "give": { // test: put a card in hand, e.g. "give BoardGame:BGPredator"
                AbstractCard c = com.megacrit.cardcrawl.helpers.CardLibrary.getCard(a[1].replace('_', ' '));
                if (c == null) return "no card " + a[1];
                c = c.makeCopy();
                if (a.length > 2) c.upgrade();
                AbstractDungeon.player.hand.addToTop(c);
                AbstractDungeon.player.hand.refreshHandLayout();
                return "gave " + c.cardID;
            }
            case "relic": { // test: obtain a relic, e.g. "relic BGOrichalcum"
                com.megacrit.cardcrawl.relics.AbstractRelic r = com.megacrit.cardcrawl.helpers.RelicLibrary.getRelic(a[1].replace('_', ' '));
                if (r == null) return "no relic " + a[1];
                r.makeCopy().instantObtain();
                return "relic " + a[1];
            }
            case "power": { // test: apply a power to yourself, e.g. "power BGWeakened 1"
                try {
                    Class<?> pc = Class.forName(a[1].contains(".") ? a[1] : "BoardGame.powers." + a[1]);
                    AbstractPower pw = null;
                    for (java.lang.reflect.Constructor<?> k : pc.getConstructors()) {
                        Class<?>[] ts = k.getParameterTypes();
                        if (ts.length < 2 || ts[1] != int.class) continue;
                        Object[] args = new Object[ts.length];
                        args[0] = AbstractDungeon.player;
                        args[1] = Integer.parseInt(a[2]);
                        for (int i = 2; i < ts.length; i++) args[i] = ts[i] == boolean.class ? (Object) true : null;
                        pw = (AbstractPower) k.newInstance(args);
                        break;
                    }
                    AbstractDungeon.actionManager.addToBottom(new com.megacrit.cardcrawl.actions.common.ApplyPowerAction(
                            AbstractDungeon.player, AbstractDungeon.player, pw, Integer.parseInt(a[2])));
                    return "power " + pw.ID;
                } catch (Throwable t) {
                    return "power failed " + t;
                }
            }
            case "potion": {
                com.megacrit.cardcrawl.potions.AbstractPotion pt = com.megacrit.cardcrawl.helpers.PotionHelper.getPotion(a[1].replace('_', ' '));
                if (pt == null) return "no potion " + a[1];
                AbstractDungeon.player.obtainPotion(pt);
                return "potion " + a[1];
            }
            case "orb": { // test: channel an orb, e.g. "orb BGFrost"
                try {
                    com.megacrit.cardcrawl.orbs.AbstractOrb o = (com.megacrit.cardcrawl.orbs.AbstractOrb)
                            Class.forName("BoardGame.orbs." + a[1]).getConstructor().newInstance();
                    AbstractDungeon.player.channelOrb(o);
                    return "orb " + a[1];
                } catch (Throwable t) {
                    return "orb failed " + t;
                }
            }
            case "event": { // test: in an event room, replace the event, e.g. "event BGLab" (run in every game)
                try {
                    com.megacrit.cardcrawl.events.AbstractEvent ev = (com.megacrit.cardcrawl.events.AbstractEvent)
                            Class.forName("BoardGame.events." + a[1]).getConstructor().newInstance();
                    com.megacrit.cardcrawl.events.AbstractEvent old = AbstractDungeon.getCurrRoom().event;
                    if (old != null) old.imageEventText.clear();
                    com.megacrit.cardcrawl.events.GenericEventDialog.hide();
                    BoardGame.multiplayer.ui.EventDie.reset();
                    AbstractDungeon.getCurrRoom().event = ev;
                    ev.onEnterRoom();
                    return "event " + ev.getClass().getSimpleName();
                } catch (Throwable t) {
                    return "event failed " + t;
                }
            }
            case "setroom": { // test: make map node x,y a given room type, e.g. "setroom 3 0 shop"
                com.megacrit.cardcrawl.map.MapRoomNode n = AbstractDungeon.map.get(Integer.parseInt(a[2])).get(Integer.parseInt(a[1]));
                switch (a[3]) {
                    case "shop": n.room = new com.megacrit.cardcrawl.rooms.ShopRoom(); break;
                    case "rest": n.room = new com.megacrit.cardcrawl.rooms.RestRoom(); break;
                    case "event": n.room = new com.megacrit.cardcrawl.rooms.EventRoom(); break;
                    case "treasure": n.room = new com.megacrit.cardcrawl.rooms.TreasureRoom(); break;
                    case "elite": n.room = new com.megacrit.cardcrawl.rooms.MonsterRoomElite(); break;
                    case "boss": n.room = new com.megacrit.cardcrawl.rooms.MonsterRoomBoss(); break;
                    default: n.room = new com.megacrit.cardcrawl.rooms.MonsterRoom();
                }
                return "node " + a[1] + "," + a[2] + " is now " + n.room.getClass().getSimpleName();
            }
            case "shopdump": {
                com.megacrit.cardcrawl.shop.ShopScreen sh = AbstractDungeon.shopScreen;
                if (sh == null) return "no shop";
                StringBuilder sb = new StringBuilder("colored=");
                for (AbstractCard c : sh.coloredCards) sb.append(c.cardID).append(' ');
                sb.append("\ncolorless=");
                for (AbstractCard c : sh.colorlessCards) sb.append(c.cardID).append(' ');
                java.util.ArrayList<com.megacrit.cardcrawl.shop.StoreRelic> rs = basemod.ReflectionHacks.getPrivate(sh,
                        com.megacrit.cardcrawl.shop.ShopScreen.class, "relics");
                sb.append("\nrelics=");
                for (com.megacrit.cardcrawl.shop.StoreRelic r : rs) sb.append(r.relic.relicId).append(r.isPurchased ? "(sold) " : " ");
                return sb.toString();
            }
            case "buycard": { // test: buy shop card by list/index, e.g. "buycard colorless 0"
                com.megacrit.cardcrawl.shop.ShopScreen sh = AbstractDungeon.shopScreen;
                java.util.ArrayList<AbstractCard> l = a[1].equals("colorless") ? sh.colorlessCards : sh.coloredCards;
                AbstractCard c = l.get(Integer.parseInt(a[2]));
                AbstractDungeon.player.gold += c.price;
                try {
                    java.lang.reflect.Method m = com.megacrit.cardcrawl.shop.ShopScreen.class.getDeclaredMethod("purchaseCard", AbstractCard.class);
                    m.setAccessible(true);
                    m.invoke(sh, c);
                } catch (Throwable t) {
                    return "buy failed " + t;
                }
                return "bought " + c.cardID;
            }
            case "maxhp": // test: survive long fights
                AbstractDungeon.player.increaseMaxHp(Integer.parseInt(a[1]), false);
                AbstractDungeon.player.heal(999);
                return "hp " + AbstractDungeon.player.currentHealth;
            case "energy":
                AbstractDungeon.player.gainEnergy(Integer.parseInt(a[1]));
                return "energy";
            case "monsters": {
                StringBuilder sb = new StringBuilder();
                for (com.megacrit.cardcrawl.monsters.AbstractMonster m : AbstractDungeon.getMonsters().monsters)
                    sb.append(BoardGame.multiplayer.combat.MonsterSync.id(m)).append(' ').append(m.getClass().getSimpleName())
                            .append(" row").append(BoardGame.multiplayer.combat.MonsterSync.row(m)).append(" hp")
                            .append(m.currentHealth).append(m.isDeadOrEscaped() ? " DEAD" : "").append(" x")
                            .append((int) m.drawX).append('\n');
                return sb.toString();
            }
            case "end":
                if (!UndoManager.isIdle()) return "not idle";
                AbstractDungeon.overlayMenu.endTurnButton.disable(true);
                return "ending turn";
            default:
                return "unknown command";
        }
    }

    /** One-line-per-section dump of everything gameplay-relevant; equal dumps mean equal state. */
    public static String state() {
        if (!CardCrawlGame.isInARun() || AbstractDungeon.player == null) return "not in a run";
        AbstractPlayer p = AbstractDungeon.player;
        StringBuilder sb = new StringBuilder();
        sb.append("turn=").append(GameActionManager.turn).append(" hp=").append(p.currentHealth).append('/')
                .append(p.maxHealth).append(" block=").append(p.currentBlock).append(" energy=")
                .append(com.megacrit.cardcrawl.ui.panels.EnergyPanel.totalCount).append(" gold=").append(p.gold)
                .append(" stance=").append(p.stance == null ? "-" : p.stance.ID).append(" die=")
                .append(TheDie.initialRoll).append('/').append(TheDie.finalRelicRoll).append('/')
                .append(TheDie.monsterRoll).append(" lock=").append(TheDie.forceLockInRoll).append('\n');
        sb.append("powers=").append(powers(p.powers)).append('\n');
        sb.append("hand=").append(cards(p.hand)).append('\n');
        sb.append("draw=").append(cards(p.drawPile)).append('\n');
        sb.append("discard=").append(cards(p.discardPile)).append('\n');
        sb.append("exhaust=").append(cards(p.exhaustPile)).append('\n');
        sb.append("relics=");
        for (AbstractRelic r : p.relics)
            sb.append(r.relicId).append(':').append(r.counter).append(r.grayscale ? "g" : "").append(' ');
        sb.append("\npotions=");
        for (AbstractPotion po : p.potions) sb.append(po.ID).append(' ');
        sb.append("\norbs=");
        for (AbstractOrb o : p.orbs) sb.append(o.ID).append(':').append(o.passiveAmount).append(' ');
        sb.append('\n');
        if (AbstractDungeon.currMapNode != null && AbstractDungeon.getCurrRoom().monsters != null) {
            for (AbstractMonster m : AbstractDungeon.getCurrRoom().monsters.monsters) {
                sb.append("monster ").append(BoardGame.multiplayer.combat.MonsterSync.id(m)).append(" row")
                        .append(BoardGame.multiplayer.combat.MonsterSync.row(m)).append(' ').append(m.id).append(" hp=").append(m.currentHealth).append('/')
                        .append(m.maxHealth).append(" block=").append(m.currentBlock).append(" dead=")
                        .append(m.isDead).append('/').append(m.isDying).append('/').append(m.escaped)
                        .append(" intent=").append(m.intent).append(" move=").append(m.nextMove).append(" dmg=")
                        .append(m.getIntentDmg()).append(" dt=").append(deathTimer(m)).append(" powers=").append(powers(m.powers)).append('\n');
            }
        }
        sb.append("rng=");
        Random[] rs = {AbstractDungeon.monsterRng, AbstractDungeon.aiRng, AbstractDungeon.shuffleRng,
                AbstractDungeon.cardRandomRng, AbstractDungeon.miscRng, AbstractDungeon.cardRng,
                AbstractDungeon.monsterHpRng, AbstractDungeon.potionRng, AbstractDungeon.relicRng};
        for (Random r : rs) sb.append(r == null ? -1 : r.counter).append(' ');
        sb.append("\nplayed=").append(AbstractDungeon.actionManager.cardsPlayedThisTurn.size()).append('/')
                .append(AbstractDungeon.actionManager.cardsPlayedThisCombat.size()).append(" undoDepth=")
                .append(UndoManager.undoDepth());
        return sb.toString();
    }

    private static String deathTimer(AbstractMonster m) {
        try {
            java.lang.reflect.Field f = AbstractMonster.class.getDeclaredField("deathTimer");
            f.setAccessible(true);
            return String.valueOf(f.getFloat(m));
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String powers(java.util.ArrayList<AbstractPower> ps) {
        StringBuilder sb = new StringBuilder();
        for (AbstractPower pw : ps) sb.append(pw.ID).append(':').append(pw.amount).append(' ');
        return sb.toString();
    }

    private static String cards(CardGroup g) {
        StringBuilder sb = new StringBuilder();
        for (AbstractCard c : g.group)
            sb.append(c.cardID).append(c.upgraded ? "+" : "").append('(').append(c.costForTurn).append(',')
                    .append(c.baseDamage).append(',').append(c.baseBlock).append(',').append(c.baseMagicNumber)
                    .append(',').append(c.misc).append(") ");
        return sb.toString();
    }
}

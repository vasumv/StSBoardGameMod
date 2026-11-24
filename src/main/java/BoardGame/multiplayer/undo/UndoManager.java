package BoardGame.multiplayer.undo;

import BoardGame.multiplayer.debug.DebugChannel;
import basemod.BaseMod;
import basemod.interfaces.PostUpdateSubscriber;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.vfx.TextAboveCreatureEffect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

/**
 * Undo for combat. Takes a snapshot each time the game settles into an idle state
 * during the player's turn, and pushes the previous one as an undo point when
 * something starts happening (a card, potion, relic click...). Ctrl+Z undoes the
 * last step, Ctrl+R returns to the start of the turn. Undo points do not survive
 * the end of the turn.
 */
@SpireInitializer
public class UndoManager implements PostUpdateSubscriber {
    public static final Logger logger = LogManager.getLogger(UndoManager.class.getName());
    private static final float SETTLE_SECONDS = 0.15f;

    /** One resolved step this turn: the state before it and what it was. */
    public static final class Step {
        public final StateSnapshot before;
        public final String label;

        Step(StateSnapshot before, String label) {
            this.before = before;
            this.label = label;
        }
    }

    private static final ArrayList<Step> stack = new ArrayList<>();
    private static int playedAtSnapshot = 0;
    private static StateSnapshot current;
    private static boolean dirty = true;
    private static float idleTime = 0f;
    private static int trackedTurn = -1;
    private static AbstractRoom trackedRoom;

    public static void initialize() {
        BaseMod.subscribe(new UndoManager());
        BaseMod.subscribe(new HistoryPanel());
        BaseMod.subscribe(new BoardGame.multiplayer.ui.MPOverlay());
    }

    public static int undoDepth() {
        return stack.size();
    }

    /** Steps resolved this turn, oldest first. */
    public static java.util.List<Step> steps() {
        return java.util.Collections.unmodifiableList(stack);
    }

    public static StateSnapshot currentSnapshot() {
        return current;
    }

    private static boolean inCombat() {
        return CardCrawlGame.isInARun() && AbstractDungeon.player != null && AbstractDungeon.currMapNode != null
                && AbstractDungeon.getCurrRoom() != null
                && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT;
    }

    private static boolean managerBusy() {
        GameActionManager am = AbstractDungeon.actionManager;
        return !am.actions.isEmpty() || !am.preTurnActions.isEmpty() || !am.cardQueue.isEmpty()
                || am.currentAction != null || am.usingCard || am.turnHasEnded
                || am.phase != GameActionManager.Phase.WAITING_ON_USER;
    }

    /** True when the player could act right now and nothing is resolving. */
    public static boolean isIdle() {
        if (!inCombat() || managerBusy()) return false;
        AbstractPlayer p = AbstractDungeon.player;
        return !AbstractDungeon.isScreenUp && !p.isEndingTurn && !p.endTurnQueued && !p.isDead
                && !AbstractDungeon.getCurrRoom().isBattleOver && AbstractDungeon.overlayMenu.endTurnButton.enabled;
    }

    /** True when undo() would do something right now. */
    public static boolean canUndo() {
        return !dirty && current != null && !stack.isEmpty() && isIdle();
    }

    @Override
    public void receivePostUpdate() {
        try {
            BoardGame.multiplayer.MP.update();
            BoardGame.multiplayer.RunStart.update();
            BoardGame.multiplayer.ui.MPOverlay.update();
            tick();
            HistoryPanel.update();
            DebugChannel.poll();
        } catch (Throwable t) {
            logger.error("undo tick failed", t);
        }
    }

    private static void tick() {
        if (BoardGame.multiplayer.MP.inRun) {
            // in multiplayer, steps are the shared play queue (see Session)
            if (current != null || !stack.isEmpty()) clear();
            return;
        }
        if (!inCombat()) {
            if (current != null || !stack.isEmpty()) clear();
            return;
        }
        if (GameActionManager.turn != trackedTurn || AbstractDungeon.getCurrRoom() != trackedRoom) {
            clear();
            trackedTurn = GameActionManager.turn;
            trackedRoom = AbstractDungeon.getCurrRoom();
        }
        if (isIdle()) {
            idleTime += Gdx.graphics.getDeltaTime();
            if (dirty && idleTime >= SETTLE_SECONDS) {
                current = StateSnapshot.capture();
                playedAtSnapshot = AbstractDungeon.actionManager.cardsPlayedThisTurn.size();
                dirty = false;
            }
            if (!dirty && Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)) {
                if (Gdx.input.isKeyJustPressed(Input.Keys.Z)) undo();
                else if (Gdx.input.isKeyJustPressed(Input.Keys.R)) resetTurn();
            }
        } else {
            idleTime = 0f;
            if (managerBusy() && !dirty) {
                // something started resolving: the last settled state becomes an undo point
                stack.add(new Step(current, describeActivity()));
                dirty = true;
            }
        }
    }

    private static void clear() {
        stack.clear();
        current = null;
        dirty = true;
        idleTime = 0f;
    }

    private static String describeActivity() {
        GameActionManager am = AbstractDungeon.actionManager;
        if (!am.cardQueue.isEmpty() && am.cardQueue.get(0).card != null) {
            AbstractMonster m = am.cardQueue.get(0).monster;
            return am.cardQueue.get(0).card.name + (m != null ? " > " + m.name : "");
        }
        if (am.cardsPlayedThisTurn.size() > playedAtSnapshot)
            return am.cardsPlayedThisTurn.get(am.cardsPlayedThisTurn.size() - 1).name;
        if (am.turnHasEnded || AbstractDungeon.player.isEndingTurn || AbstractDungeon.player.endTurnQueued)
            return "End turn";
        return "Other action";
    }

    public static boolean undo() {
        return rollbackTo(stack.size() - 1);
    }

    public static boolean resetTurn() {
        return rollbackTo(0);
    }

    /** Restore the state from before step {@code index}; that step and all later ones are undone. */
    public static boolean rollbackTo(int index) {
        if (!canUndo() || index < 0 || index >= stack.size()) return false;
        Step target = stack.get(index);
        String label = index == stack.size() - 1 ? "Undo: " + target.label : "Rolled back to before " + target.label;
        while (stack.size() > index) stack.remove(stack.size() - 1);
        apply(target.before, label);
        return true;
    }

    /** Restore a snapshot taken elsewhere (multiplayer rollback). */
    public static void restore(StateSnapshot s, String label) {
        apply(s, label);
        clear();
    }

    private static void apply(StateSnapshot s, String label) {
        s.restore();
        current = s;
        dirty = false;
        playedAtSnapshot = AbstractDungeon.actionManager.cardsPlayedThisTurn.size();
        AbstractPlayer p = AbstractDungeon.player;
        // the snapshot may have been taken while a card was hovered
        p.hoveredCard = null;
        p.toHover = null;
        p.isDraggingCard = false;
        p.inSingleTargetMode = false;
        p.hand.refreshHandLayout();
        p.hand.applyPowers();
        p.hand.glowCheck();
        for (AbstractMonster m : AbstractDungeon.getCurrRoom().monsters.monsters) {
            if (!m.isDeadOrEscaped()) m.applyPowers();
        }
        AbstractDungeon.effectList.add(new TextAboveCreatureEffect(p.hb.cX, p.hb.cY + p.hb.height / 2f, label,
                Color.WHITE.cpy()));
        logger.info(label + ": restored " + s.objectCount() + " objects, " + stack.size() + " undo points left");
    }
}

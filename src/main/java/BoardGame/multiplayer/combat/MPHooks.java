package BoardGame.multiplayer.combat;

import BoardGame.dungeons.BGExordium;
import BoardGame.multicharacter.MultiCreature;
import BoardGame.multiplayer.MP;
import BoardGame.multiplayer.Session;
import BoardGame.thedie.TheDie;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.actions.common.MonsterStartTurnAction;
import com.megacrit.cardcrawl.cards.CardQueueItem;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.MonsterHelper;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoomBoss;
import com.megacrit.cardcrawl.rooms.MonsterRoomElite;
import com.megacrit.cardcrawl.ui.buttons.EndTurnButton;
import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.lang.reflect.Method;
import java.util.ArrayList;

/** All game patches for multiplayer. Each one does nothing unless a multiplayer run is active. */
public class MPHooks {
    public static AbstractMonster actingMonster;

    private static boolean multi() {
        return MP.inRun && MP.playerCount() > 1;
    }

    // ---------------- card plays go through the shared queue ----------------

    @SpirePatch2(clz = GameActionManager.class, method = "getNextAction")
    public static class InterceptCardPlay {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(GameActionManager __instance) {
            if (!MP.inRun || !MonsterSync.active()) return SpireReturn.Continue();
            if (!__instance.actions.isEmpty() || !__instance.preTurnActions.isEmpty() || __instance.cardQueue.isEmpty())
                return SpireReturn.Continue();
            CardQueueItem head = __instance.cardQueue.get(0);
            if (head.card == null || head == Session.authorizedItem || head.autoplayCard || head.isEndTurnAutoPlay)
                return SpireReturn.Continue();
            if (!AbstractDungeon.player.hand.contains(head.card)) return SpireReturn.Continue();
            __instance.cardQueue.remove(0);
            if (!Session.endTurnPending) Session.requestPlay(head);
            AbstractDungeon.player.hand.refreshHandLayout();
            return SpireReturn.Return();
        }
    }

    // ---------------- end turn = ready vote ----------------

    @SpirePatch2(clz = EndTurnButton.class, method = "disable", paramtypez = {boolean.class})
    public static class EndTurnIsReady {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(EndTurnButton __instance, boolean isEnemyTurn) {
            if (!MP.inRun || !isEnemyTurn || Session.authorizedEndTurn || !MonsterSync.active())
                return SpireReturn.Continue();
            Session.toggleReady();
            return SpireReturn.Return();
        }
    }

    // ---------------- enemy turn: barrier, acting monster, row filtering ----------------

    @SpirePatch2(clz = MonsterStartTurnAction.class, method = "update")
    public static class EnemyTurnBarrier {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix() {
            if (MP.inRun && Session.holdBeforeEnemyTurn()) return SpireReturn.Return();
            return SpireReturn.Continue();
        }
    }

    @SpirePatch2(clz = GameActionManager.class, method = "getNextAction")
    public static class TrackMonsterTurn {
        @SpireInstrumentPatch
        public static ExprEditor edit() {
            return new ExprEditor() {
                @Override
                public void edit(MethodCall m) throws CannotCompileException {
                    if (m.getMethodName().equals("takeTurn")) {
                        m.replace("{ BoardGame.multiplayer.combat.MPHooks.actingMonster = $0; $_ = $proceed($$);"
                                + " BoardGame.multiplayer.combat.MPHooks.actingMonster = null; }");
                    } else if (m.getMethodName().equals("applyStartOfTurnRelics")) {
                        m.replace("{ BoardGame.multiplayer.combat.MPHooks.markStartOfTurn(); $_ = $proceed($$); }");
                    }
                }
            };
        }
    }

    @SpirePatch2(clz = AbstractRoom.class, method = "update")
    public static class MarkCombatStart {
        @SpireInstrumentPatch
        public static ExprEditor edit() {
            return new ExprEditor() {
                @Override
                public void edit(MethodCall m) throws CannotCompileException {
                    if (m.getMethodName().equals("applyStartOfCombatPreDrawLogic")) {
                        m.replace("{ BoardGame.multiplayer.combat.MPHooks.markStartOfTurn(); $_ = $proceed($$); }");
                    }
                }
            };
        }
    }

    public static void markStartOfTurn() {
        if (!MP.inRun) return;
        AbstractDungeon.actionManager.addToBottom(new AbstractGameAction() {
            @Override
            public void update() {
                Session.onEnemyTurnDone();
                this.isDone = true;
            }
        });
    }

    @SpirePatch2(clz = AbstractGameAction.class, method = SpirePatch.CONSTRUCTOR)
    public static class StampActionSource {
        @SpirePostfixPatch
        public static void postfix(AbstractGameAction __instance) {
            if (actingMonster != null) MPFields.Action.sourceMonster.set(__instance, actingMonster);
        }
    }

    @SpirePatch2(clz = GameActionManager.class, method = "update")
    public static class RunActionWithSource {
        @SpireInstrumentPatch
        public static ExprEditor edit() {
            return new ExprEditor() {
                @Override
                public void edit(MethodCall m) throws CannotCompileException {
                    if (m.getMethodName().equals("update")
                            && m.getClassName().equals(AbstractGameAction.class.getName())) {
                        m.replace("{ BoardGame.multiplayer.combat.MPHooks.runAction($0); }");
                    }
                }
            };
        }
    }

    public static void runAction(AbstractGameAction a) {
        AbstractMonster src = MPFields.Action.sourceMonster.get(a);
        if (src == null || !multi()) {
            a.update();
            return;
        }
        if (!MonsterSync.targetsMe(src) && affectsPlayer(a)) {
            a.isDone = true; // an enemy in another player's row: its attack is aimed at them
            return;
        }
        AbstractMonster prev = actingMonster;
        actingMonster = src;
        try {
            a.update();
        } finally {
            actingMonster = prev;
        }
    }

    private static boolean affectsPlayer(AbstractGameAction a) {
        if (a.target == AbstractDungeon.player) return true;
        if (a.target != null) return false;
        String n = a.getClass().getSimpleName();
        return n.contains("MakeTempCard") || n.contains("Daze") || n.contains("Burn") || n.contains("Wound")
                || n.contains("Slime") || n.contains("Status") || n.contains("Void");
    }

    // ---------------- one shared die per round ----------------

    @SpirePatch2(clz = TheDie.class, method = "roll")
    public static class SharedDie {
        @SpireInstrumentPatch
        public static ExprEditor edit() {
            return new ExprEditor() {
                @Override
                public void edit(MethodCall m) throws CannotCompileException {
                    if (m.getMethodName().equals("random") && m.getClassName().equals(
                            "com.megacrit.cardcrawl.random.Random")) {
                        m.replace("{ if (BoardGame.multiplayer.MP.inRun) $_ = BoardGame.multiplayer.combat.MPHooks.sharedDie();"
                                + " else $_ = $proceed($$); }");
                    }
                }
            };
        }
    }

    public static int sharedDie() {
        long s = MP.seed * 1000003L + AbstractDungeon.floorNum * 1009L + GameActionManager.turn * 31L;
        return new java.util.Random(s).nextInt(6) + 1;
    }

    // ---------------- rows: one encounter per player ----------------

    @SpirePatch2(clz = BGExordium.class, method = "generateWeakEnemies")
    public static class FirstEncounterPerPlayer {
        @SpirePrefixPatch
        public static void prefix(@ByRef int[] count) {
            if (multi()) count[0] = Math.max(count[0], MP.playerCount());
        }
    }

    @SpirePatch2(clz = MonsterRoom.class, method = "onPlayerEntry")
    public static class BuildRows {
        @SpirePrefixPatch
        public static void prefix(MonsterRoom __instance) {
            if (!MP.inRun || __instance.monsters != null) return;
            if (__instance instanceof MonsterRoomElite || __instance instanceof MonsterRoomBoss) return;
            int n = MP.playerCount();
            if (n < 2) return;
            try {
                while (AbstractDungeon.monsterList.size() < n) {
                    Method gen = findMethod(CardCrawlGame.dungeon.getClass(), "generateStrongEnemies", int.class);
                    gen.setAccessible(true);
                    gen.invoke(CardCrawlGame.dungeon, 12);
                }
                ArrayList<String> keys = new ArrayList<>(AbstractDungeon.monsterList.subList(0, n));
                for (int i = 1; i < n; i++) AbstractDungeon.monsterList.remove(1);
                int mine = Math.min(MP.myRow(), n - 1);
                AbstractDungeon.monsterList.set(0, keys.get(mine));
                AbstractDungeon.lastCombatMetricKey = keys.get(mine);
                // build every row in a fixed order so random draws match in every game
                ArrayList<AbstractMonster> all = new ArrayList<>();
                for (int row = 0; row < n; row++) {
                    MonsterGroup g = MonsterHelper.getEncounter(keys.get(row));
                    for (AbstractMonster m : g.monsters) {
                        MultiCreature.Field.currentRow.set(m, row);
                        all.add(m);
                    }
                }
                // enemies act from the highest row down
                ArrayList<AbstractMonster> ordered = new ArrayList<>();
                for (int row = n - 1; row >= 0; row--)
                    for (AbstractMonster m : all) if (MonsterSync.row(m) == row) ordered.add(m);
                MonsterGroup group = new MonsterGroup(ordered.toArray(new AbstractMonster[0]));
                for (AbstractMonster m : ordered) MonsterSync.applyLayout(m);
                __instance.monsters = group;
                MonsterSync.resetIds();
                MonsterSync.assignIds();
                group.init();
            } catch (Throwable t) {
                MP.logger.error("could not build rows", t);
            }
        }
    }

    private static Method findMethod(Class<?> c, String name, Class<?>... args) throws NoSuchMethodException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                return k.getDeclaredMethod(name, args);
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(name);
    }

    // ---------------- map: the party moves together ----------------

    private static float timerBefore;
    private static boolean firstChosenBefore;

    @SpirePatch2(clz = MapRoomNode.class, method = "update")
    public static class MapVote {
        @SpirePrefixPatch
        public static void prefix(MapRoomNode __instance, float ___animWaitTimer) {
            timerBefore = ___animWaitTimer;
            firstChosenBefore = AbstractDungeon.firstRoomChosen;
        }

        @SpirePostfixPatch
        public static void postfix(MapRoomNode __instance, @ByRef float[] ___animWaitTimer) {
            if (!MP.inRun || MP.playerCount() < 2) return;
            if (timerBefore == 0f && ___animWaitTimer[0] > 0f && __instance != Session.authorizedNode) {
                ___animWaitTimer[0] = 0f;
                AbstractDungeon.firstRoomChosen = firstChosenBefore;
                Session.voteMap(__instance.x, __instance.y);
            }
        }
    }

    // ---------------- keep dead enemies' textures until the room ends (rollback can revive them) ----------------

    private static final ArrayList<AbstractMonster> deferred = new ArrayList<>();
    private static boolean disposingNow = false;

    @SpirePatch2(clz = AbstractMonster.class, method = "dispose")
    public static class DeferDispose {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(AbstractMonster __instance) {
            if (disposingNow || !CardCrawlGame.isInARun() || AbstractDungeon.currMapNode == null
                    || AbstractDungeon.getCurrRoom().phase != AbstractRoom.RoomPhase.COMBAT)
                return SpireReturn.Continue();
            if (!deferred.contains(__instance)) deferred.add(__instance);
            return SpireReturn.Return();
        }
    }

    public static void disposeDeferred() {
        disposingNow = true;
        try {
            for (AbstractMonster m : deferred) {
                try {
                    m.dispose();
                } catch (Throwable ignored) {
                }
            }
        } finally {
            deferred.clear();
            disposingNow = false;
        }
    }
}

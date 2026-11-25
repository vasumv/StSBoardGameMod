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
    public static com.megacrit.cardcrawl.cards.AbstractCard actingCard;

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
            if (actingCard != null) MPFields.Action.sourceCard.set(__instance, actingCard);
        }
    }

    @SpirePatch2(clz = com.megacrit.cardcrawl.characters.AbstractPlayer.class, method = "useCard")
    public static class TrackCardUse {
        @SpirePrefixPatch
        public static void prefix(com.megacrit.cardcrawl.cards.AbstractCard c) {
            actingCard = c;
        }

        @SpirePostfixPatch
        public static void postfix() {
            actingCard = null;
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
        com.megacrit.cardcrawl.cards.AbstractCard card = MPFields.Action.sourceCard.get(a);
        if (card != null && multi() && MonsterSync.active() && Session.redirectToTeammate(a, card)) {
            a.isDone = true;
            return;
        }
        com.megacrit.cardcrawl.cards.AbstractCard prevCard = actingCard;
        if (card != null) actingCard = card;
        try {
            runMonsterAction(a);
        } finally {
            actingCard = prevCard;
        }
    }

    private static void runMonsterAction(AbstractGameAction a) {
        AbstractMonster src = MPFields.Action.sourceMonster.get(a);
        if (src == null || !multi()) {
            a.update();
            return;
        }
        if (MonsterSync.active() && !MonsterSync.targetsMe(src) && affectsPlayer(a)) {
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

    // the shared die lives in DiePhase

    // ---------------- rows: one encounter per player ----------------

    @SpirePatch2(clz = BGExordium.class, method = "generateWeakEnemies")
    public static class FirstEncounterPerPlayer {
        @SpirePrefixPatch
        public static void prefix(@ByRef int[] count) {
            if (multi()) count[0] = Math.max(count[0], MP.playerCount());
        }
    }

    /** Normal encounters (map rooms and forced event fights): one encounter per player row. */
    @SpirePatch2(clz = AbstractDungeon.class, method = "getMonsterForRoomCreation")
    public static class BuildRows {
        @SpirePrefixPatch
        public static SpireReturn<MonsterGroup> prefix(AbstractDungeon __instance) {
            if (!multi() || optionalEventFight()) return SpireReturn.Continue();
            int n = MP.playerCount();
            try {
                while (AbstractDungeon.monsterList.size() < n) {
                    Method gen = findMethod(__instance.getClass(), "generateStrongEnemies", int.class);
                    gen.setAccessible(true);
                    gen.invoke(__instance, 12);
                }
                ArrayList<String> keys = new ArrayList<>(AbstractDungeon.monsterList.subList(0, n));
                for (int i = 1; i < n; i++) AbstractDungeon.monsterList.remove(1);
                int mine = Math.min(MP.myRow(), n - 1);
                AbstractDungeon.monsterList.set(0, keys.get(mine)); // rewards come from your own row
                AbstractDungeon.lastCombatMetricKey = keys.get(mine);
                ArrayList<AbstractMonster> all = new ArrayList<>();
                for (int row = 0; row < n; row++) {
                    MonsterGroup g = MonsterHelper.getEncounter(keys.get(row));
                    for (AbstractMonster m : g.monsters) {
                        MultiCreature.Field.currentRow.set(m, row);
                        all.add(m);
                    }
                }
                return SpireReturn.Return(finishGroup(all));
            } catch (Throwable t) {
                MP.logger.error("could not build rows", t);
                return SpireReturn.Continue();
            }
        }
    }

    /** Elites: the Elite sits in the bottom row; the rest of its group is spread over the other rows. */
    @SpirePatch2(clz = AbstractDungeon.class, method = "getEliteMonsterForRoomCreation")
    public static class EliteRows {
        @SpirePostfixPatch
        public static MonsterGroup postfix(MonsterGroup __result) {
            if (!multi() || optionalEventFight() || __result == null) return __result;
            int n = MP.playerCount();
            ArrayList<AbstractMonster> all = new ArrayList<>(__result.monsters);
            for (int i = 0; i < all.size(); i++) MultiCreature.Field.currentRow.set(all.get(i), i == 0 ? 0 : i % n);
            if (!all.isEmpty()) scaleHp(all.get(0), n);
            return finishGroup(all);
        }
    }

    /** Bosses are in every row and attack everyone. */
    @SpirePatch2(clz = AbstractDungeon.class, method = "getBoss")
    public static class BossRows {
        @SpirePostfixPatch
        public static MonsterGroup postfix(MonsterGroup __result) {
            if (!multi() || __result == null) return __result;
            for (AbstractMonster m : __result.monsters) {
                MultiCreature.Field.currentRow.set(m, 0);
                if (m.type == AbstractMonster.EnemyType.BOSS) scaleHp(m, MP.playerCount());
            }
            MonsterSync.sharedRoom = AbstractDungeon.getCurrRoom();
            MonsterSync.resetIds();
            for (AbstractMonster m : __result.monsters) MPFields.Monster.id.set(m, MonsterSync.nextId());
            return __result;
        }
    }

    /**
     * Enemy HP for bigger parties. The board game's HP board values are not in the mod;
     * this scales the solo value linearly with the number of players.
     */
    private static void scaleHp(AbstractMonster m, int players) {
        m.maxHealth = m.maxHealth * players;
        m.currentHealth = m.maxHealth;
    }

    private static MonsterGroup finishGroup(ArrayList<AbstractMonster> all) {
        int n = MP.playerCount();
        // enemies act from the highest row down
        ArrayList<AbstractMonster> ordered = new ArrayList<>();
        for (int row = n - 1; row >= 0; row--)
            for (AbstractMonster m : all) if (MonsterSync.row(m) == row) ordered.add(m);
        MonsterGroup group = new MonsterGroup(ordered.toArray(new AbstractMonster[0]));
        for (AbstractMonster m : ordered) MonsterSync.applyLayout(m);
        MonsterSync.sharedRoom = AbstractDungeon.getCurrRoom();
        MonsterSync.resetIds();
        for (AbstractMonster m : ordered) MPFields.Monster.id.set(m, MonsterSync.nextId());
        return group;
    }

    /** Event fights that only the choosing player enters are played solo. */
    private static boolean optionalEventFight() {
        AbstractRoom r = AbstractDungeon.currMapNode == null ? null : AbstractDungeon.getCurrRoom();
        if (!(r instanceof com.megacrit.cardcrawl.rooms.EventRoom) || r.event == null) return false;
        String n = r.event.getClass().getSimpleName();
        return n.contains("Colosseum") || n.contains("DeadAdventurer");
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

    /** Clicking the boss icon is a vote too. */
    @SpirePatch2(clz = com.megacrit.cardcrawl.map.DungeonMap.class, method = "update")
    public static class BossVote {
        @SpirePrefixPatch
        public static void prefix(com.megacrit.cardcrawl.map.DungeonMap __instance) {
            if (!MP.inRun || MP.playerCount() < 2) return;
            if (AbstractDungeon.screen != AbstractDungeon.CurrentScreen.MAP) return;
            if (AbstractDungeon.getCurrRoom().phase != AbstractRoom.RoomPhase.COMPLETE) return;
            if (!__instance.bossHb.hovered || !com.megacrit.cardcrawl.helpers.input.InputHelper.justClickedLeft) return;
            // only from the last row before the boss
            MapRoomNode cur = AbstractDungeon.getCurrMapNode();
            boolean next = false;
            for (java.util.ArrayList<MapRoomNode> row : AbstractDungeon.map)
                for (MapRoomNode n : row)
                    if (n.room instanceof MonsterRoomBoss && cur.isConnectedTo(n)) next = true;
            boolean lastRow = cur.y >= AbstractDungeon.map.size() - 2;
            if (!next && !lastRow) return;
            com.megacrit.cardcrawl.helpers.input.InputHelper.justClickedLeft = false;
            Session.voteMap(-1, 99);
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

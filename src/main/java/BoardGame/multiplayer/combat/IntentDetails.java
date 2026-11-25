package BoardGame.multiplayer.combat;

import BoardGame.multiplayer.MP;
import BoardGame.multiplayer.undo.StateSnapshot;
import basemod.ReflectionHacks;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.actions.common.*;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * Hovering an enemy's intent shows exactly what it will do ("Deal 2 damage twice, gain 1
 * Strength, add a Dazed to your draw pile") instead of the generic "intends to buff".
 * The text comes from running the enemy's turn on a throwaway copy of the state: the
 * actions it would queue are recorded, the state is restored, and nothing is executed.
 */
public class IntentDetails {
    private static boolean capturing = false;
    private static final ArrayList<AbstractGameAction> captured = new ArrayList<>();
    private static final HashMap<AbstractMonster, String> cache = new HashMap<>();
    private static final HashMap<AbstractMonster, String> cacheKey = new HashMap<>();

    @SpirePatch2(clz = GameActionManager.class, method = "addToBottom")
    public static class CaptureBottom {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(AbstractGameAction action) {
            if (!capturing) return SpireReturn.Continue();
            captured.add(action);
            return SpireReturn.Return();
        }
    }

    @SpirePatch2(clz = GameActionManager.class, method = "addToTop")
    public static class CaptureTop {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(AbstractGameAction action) {
            if (!capturing) return SpireReturn.Continue();
            captured.add(0, action);
            return SpireReturn.Return();
        }
    }

    private static String key(AbstractMonster m) {
        return m.nextMove + "|" + m.intent + "|" + GameActionManager.turn + "|" + BoardGame.thedie.TheDie.monsterRoll
                + "|" + AbstractDungeon.floorNum;
    }

    private static boolean safeNow() {
        if (AbstractDungeon.currMapNode == null || AbstractDungeon.getCurrRoom() == null
                || AbstractDungeon.getCurrRoom().phase != AbstractRoom.RoomPhase.COMBAT) return false;
        GameActionManager am = AbstractDungeon.actionManager;
        return am.actions.isEmpty() && am.currentAction == null && am.cardQueue.isEmpty() && !am.turnHasEnded
                && am.phase == GameActionManager.Phase.WAITING_ON_USER;
    }

    /** Called every frame: fill in the text for an intent the mouse is on. */
    public static void update() {
        if (AbstractDungeon.currMapNode == null || AbstractDungeon.getCurrRoom() == null
                || AbstractDungeon.getCurrRoom().monsters == null) return;
        for (AbstractMonster m : AbstractDungeon.getCurrRoom().monsters.monsters) {
            if (m.isDeadOrEscaped() || !m.intentHb.hovered) continue;
            String k = key(m);
            if (!k.equals(cacheKey.get(m))) {
                if (!safeNow()) continue;
                cache.put(m, describe(m));
                cacheKey.put(m, k);
            }
            String text = cache.get(m);
            if (text != null && !text.isEmpty()) {
                PowerTip tip = ReflectionHacks.getPrivate(m, AbstractMonster.class, "intentTip");
                if (tip != null) tip.body = text;
            }
        }
    }

    /** Debug: every live enemy's intent text. */
    public static String dumpAll() {
        StringBuilder sb = new StringBuilder();
        if (!safeNow()) return "busy";
        for (AbstractMonster m : AbstractDungeon.getCurrRoom().monsters.monsters)
            if (!m.isDeadOrEscaped()) sb.append(m.id).append(" ").append(m.intent).append(": ").append(describe(m)).append("\n");
        return sb.toString();
    }

    private static String describe(AbstractMonster m) {
        StateSnapshot snap = StateSnapshot.capture();
        int effects = AbstractDungeon.effectList.size(), top = AbstractDungeon.topLevelEffects.size();
        captured.clear();
        capturing = true;
        try {
            m.takeTurn();
        } catch (Throwable t) {
            MP.logger.info("could not preview " + m.id + ": " + t);
        } finally {
            capturing = false;
            snap.restore();
            trim(AbstractDungeon.effectList, effects);
            trim(AbstractDungeon.topLevelEffects, top);
        }
        ArrayList<AbstractGameAction> acts = new ArrayList<>(captured);
        captured.clear();
        return text(m, acts);
    }

    private static void trim(ArrayList<AbstractGameEffect> l, int size) {
        while (l.size() > size) l.remove(l.size() - 1);
    }

    private static String text(AbstractMonster m, ArrayList<AbstractGameAction> acts) {
        LinkedHashMap<String, Integer> parts = new LinkedHashMap<>();
        for (AbstractGameAction a : acts) {
            String s = one(m, a);
            if (s != null) parts.merge(s, 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, Integer> e : parts.entrySet()) {
            if (sb.length() > 0) sb.append(" NL ");
            sb.append(e.getKey());
            if (e.getValue() == 2) sb.append(" twice");
            else if (e.getValue() > 2) sb.append(" ").append(e.getValue()).append(" times");
            sb.append(".");
        }
        if (MP.inRun && MP.playerCount() > 1 && MonsterSync.active()) {
            if (m.type == AbstractMonster.EnemyType.BOSS || EnemyMPData.isAoe(m.getClass().getSimpleName(), m.nextMove))
                sb.append(" NL #yHits #yall #yplayers.");
            else {
                MP.Player target = MP.playerInRow(MonsterSync.targetRow(MonsterSync.row(m)));
                if (target != null) sb.append(" NL Targets ").append(target.id == MP.myId ? "#yyou" : "#y" + target.name).append(".");
            }
        }
        return sb.toString();
    }

    private static String one(AbstractMonster m, AbstractGameAction a) {
        try {
            boolean onPlayer = a.target == AbstractDungeon.player;
            if (a instanceof DamageAction) {
                DamageInfo info = ReflectionHacks.getPrivate(a, DamageAction.class, "info");
                return onPlayer ? "Deal #b" + info.base + " damage" : null;
            }
            if (a instanceof DamageAllEnemiesAction) return null;
            if (a instanceof LoseHPAction) return onPlayer ? "You lose #b" + a.amount + " HP" : null;
            if (a instanceof GainBlockAction) return a.target == m ? "Gain #b" + a.amount + " Block" : "Give #b" + a.amount + " Block";
            if (a instanceof ApplyPowerAction) {
                AbstractPower p = ReflectionHacks.getPrivate(a, ApplyPowerAction.class, "powerToApply");
                String name = p.name == null ? p.ID : p.name;
                int amt = a.amount != 0 ? a.amount : p.amount;
                String n = amt > 0 && amt != -1 ? "#b" + amt + " " : "";
                if (a.target == AbstractDungeon.player) return "Give you " + n + "#y" + name.replace(" ", " #y");
                if (a.target == m) return "Gain " + n + "#y" + name.replace(" ", " #y");
                return "Give an enemy " + n + "#y" + name.replace(" ", " #y");
            }
            if (a instanceof MakeTempCardInDrawPileAction) {
                AbstractCard c = ReflectionHacks.getPrivate(a, MakeTempCardInDrawPileAction.class, "cardToMake");
                return "Add " + countOf(a.amount) + "#y" + c.name + " to your draw pile";
            }
            if (a instanceof MakeTempCardInDiscardAction) {
                AbstractCard c = ReflectionHacks.getPrivate(a, MakeTempCardInDiscardAction.class, "c");
                int n = ReflectionHacks.getPrivate(a, MakeTempCardInDiscardAction.class, "numCards");
                return "Add " + countOf(n) + "#y" + c.name + " to your discard pile";
            }
            if (a instanceof MakeTempCardInHandAction) {
                AbstractCard c = ReflectionHacks.getPrivate(a, MakeTempCardInHandAction.class, "c");
                return "Add " + countOf(a.amount) + "#y" + c.name + " to your hand";
            }
            if (a instanceof SpawnMonsterAction) {
                AbstractMonster s = ReflectionHacks.getPrivate(a, SpawnMonsterAction.class, "m");
                return "Summon #y" + s.name.replace(" ", " #y");
            }
            if (a instanceof HealAction) return a.target == m ? "Heal #b" + a.amount + " HP" : "Heal an enemy #b" + a.amount;
            if (a instanceof EscapeAction) return "Flee";
            if (a instanceof SuicideAction) return "Die";
            if (a instanceof RemoveSpecificPowerAction || a instanceof ReducePowerAction) return null;
        } catch (Throwable ignored) {
        }
        return null; // animations, sounds, talk, the next-move roll
    }

    private static String countOf(int n) {
        return n > 1 ? "#b" + n + " " : "a ";
    }
}

package BoardGame.patches;

import BoardGame.monsters.ActsLast;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.monsters.MonsterQueueItem;

import java.util.ArrayList;

/**
 * Board game act order. MonsterGroup.queueMonsters adds the monsters to the monster queue in list
 * order (top row first, left to right). Afterwards, stably move "acts last" monsters behind the
 * others, and Bosses behind everything. The relative order inside each group is unchanged.
 */
public class MonsterActOrderPatch {
    private static int startSize = 0;

    @SpirePatch(clz = MonsterGroup.class, method = "queueMonsters")
    public static class QueueMonstersPatch {
        @SpirePrefixPatch
        public static void Prefix(MonsterGroup __instance) {
            startSize = AbstractDungeon.actionManager.monsterQueue.size();
        }

        @SpirePostfixPatch
        public static void Postfix(MonsterGroup __instance) {
            ArrayList<MonsterQueueItem> q = AbstractDungeon.actionManager.monsterQueue;
            int from = Math.min(Math.max(0, startSize), q.size());
            if (q.size() - from < 2) return;
            ArrayList<MonsterQueueItem> normal = new ArrayList<>();
            ArrayList<MonsterQueueItem> last = new ArrayList<>();
            ArrayList<MonsterQueueItem> bosses = new ArrayList<>();
            for (int i = from; i < q.size(); i++) {
                MonsterQueueItem item = q.get(i);
                int p = priority(item.monster);
                (p == 2 ? bosses : p == 1 ? last : normal).add(item);
            }
            if (last.isEmpty() && bosses.isEmpty()) return;
            while (q.size() > from) q.remove(q.size() - 1);
            q.addAll(normal);
            q.addAll(last);
            q.addAll(bosses);
        }
    }

    /** 0 = normal, 1 = acts last, 2 = boss. */
    public static int priority(AbstractMonster m) {
        if (m == null) return 0;
        if (m.type == AbstractMonster.EnemyType.BOSS) return 2;
        try {
            if (m instanceof ActsLast && ((ActsLast) m).actsLast()) return 1;
        } catch (RuntimeException e) {
            return 0;
        }
        return 0;
    }
}

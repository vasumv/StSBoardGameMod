package BoardGame.actions;


import BoardGame.monsters.bgcity.BGGremlinLeader;
import BoardGame.monsters.bgexordium.BGGremlinAngry;
import BoardGame.monsters.bgexordium.BGGremlinFat;
import BoardGame.monsters.bgexordium.BGGremlinSneaky;
import BoardGame.monsters.bgexordium.BGGremlinWizard;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.SpawnMonsterAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;


public class BGSpawnTwoGremlinsForGremlinLeaderAction extends AbstractGameAction {
    public static final Logger logger = LogManager.getLogger(BGSpawnTwoGremlinsForGremlinLeaderAction.class.getName());
    public static final float[] POSX = new float[] { -366.0F, -170.0F };
    public static final float[] POSY = new float[] { -4.0F, 6.0F };
    public BGGremlinLeader leader;

    public BGSpawnTwoGremlinsForGremlinLeaderAction(BGGremlinLeader m) {
        this.leader=m;
    }


    /** The gremlins each Leader currently has, per slot (the old check looked at list positions,
     *  which hold the encounter's placeholders, so every Rally added two more instead of reviving). */
    private static final java.util.WeakHashMap<BGGremlinLeader, AbstractMonster[]> slots = new java.util.WeakHashMap<>();

    private String getGremlinInSlot(int slot) {
        AbstractMonster[] mine = slots.computeIfAbsent(this.leader, k -> new AbstractMonster[2]);
        AbstractMonster g = mine[slot];
        logger.info("checking gremlin slot "+slot);
        if (g == null || g.isDying || g.isDead || g.escaped || !AbstractDungeon.getMonsters().monsters.contains(g)) {
            logger.info("return "+this.leader.gremlintypes[slot]);
            return this.leader.gremlintypes[slot];

        }
        logger.info("...it's apparently occupied");
        return "";
    }


    public void update() {
        String[] gremlintypes = this.leader.gremlintypes;
        AbstractMonster gremlin;
        for (int i=0;i<=1;i++) {
            String type = getGremlinInSlot(i);

            gremlin=null;
            if (type == "BGGremlinAngry") {
                gremlin = new BGGremlinAngry(POSX[i], POSY[i], false);
            } else if (type == "BGGremlinSneaky") {
                gremlin = new BGGremlinSneaky(POSX[i], POSY[i], false);
            } else if (type == "BGGremlinFat") {
                gremlin = new BGGremlinFat(POSX[i], POSY[i]);
            } else if (type == "BGGremlinWizard") {
                gremlin = new BGGremlinWizard(POSX[i], POSY[i]);
            } else {
                //gremlin not dead; don't resummon
                logger.info("Not dead, don't resummon");
            }
            logger.info("Gremlin type: "+type);
            logger.info("Gremlin: "+gremlin);
            if (gremlin != null) {
                slots.computeIfAbsent(this.leader, k -> new AbstractMonster[2])[i] = gremlin;
                AbstractDungeon.actionManager.addToTop((AbstractGameAction) new SpawnMonsterAction(gremlin, false));
            }

        }
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new ApplyAngerToGremlinsAction());
        this.isDone = true;
    }
}


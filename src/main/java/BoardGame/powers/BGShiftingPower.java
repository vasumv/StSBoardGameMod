package BoardGame.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class BGShiftingPower extends AbstractBGPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("BoardGame:BGShiftingPower");
    public static final String POWER_ID = "BGShiftingPower";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public BGShiftingPower(AbstractCreature owner) {
        this.name = NAME;
        this.ID = "BGShiftingPower";
        this.owner = owner;
        updateDescription();
        this.isPostActionPower = true;
        loadRegion("shift");
    }


    public int onAttacked(DamageInfo info, int damageAmount) {
        if (damageAmount > 0) {
            // multiplayer: the Block goes to the player in the Transient's row
            BoardGame.multiplayer.MP.Player rowOwner = BoardGame.multiplayer.combat.OtherPlayers.multi()
                    && this.owner instanceof com.megacrit.cardcrawl.monsters.AbstractMonster
                    ? BoardGame.multiplayer.MP.playerInRow(BoardGame.multiplayer.combat.MonsterSync.targetRow(
                    BoardGame.multiplayer.combat.MonsterSync.row((com.megacrit.cardcrawl.monsters.AbstractMonster) this.owner)))
                    : null;
            if (rowOwner != null && rowOwner.id != BoardGame.multiplayer.MP.myId)
                BoardGame.multiplayer.combat.OtherPlayers.giveBlock(rowOwner.id, damageAmount, "Transient");
            else
            addToTop((AbstractGameAction)new GainBlockAction(AbstractDungeon.player,damageAmount));

            flash();
        }

        return damageAmount;
    }


    public void updateDescription() {
        this.description = DESCRIPTIONS[1];
    }
}



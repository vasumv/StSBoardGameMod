package BoardGame.actions;

import BoardGame.thedie.TheDie;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.StrengthPower;

public class BGSpotWeaknessAction extends AbstractGameAction {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("OpeningAction");
    public static final String[] TEXT = uiStrings.TEXT;

    private int damageIncrease;
    private int maxDieNumber;
    private AbstractMonster targetMonster;

    public BGSpotWeaknessAction(int damageIncrease, int maxDieNumber) {
        this.duration = 0.0F;
        this.actionType = AbstractGameAction.ActionType.WAIT;
        this.damageIncrease = damageIncrease;
        this.maxDieNumber=maxDieNumber;
    }


    public void update() {
        if (TheDie.monsterRoll <= this.maxDieNumber && BoardGame.multiplayer.combat.OtherPlayers.multi()) {
            final int amt = this.damageIncrease;
            BoardGame.multiplayer.combat.OtherPlayers.askRecipient("Spot Weakness: who gets " + amt + " Strength?",
                    id -> BoardGame.multiplayer.combat.OtherPlayers.giveStrength(id, amt, "Spot Weakness"));
        } else if (TheDie.monsterRoll <= this.maxDieNumber) {
            addToBot((AbstractGameAction)new ApplyPowerAction((AbstractCreature)AbstractDungeon.player, (AbstractCreature)AbstractDungeon.player, (AbstractPower)new StrengthPower((AbstractCreature)AbstractDungeon.player, this.damageIncrease), this.damageIncrease));


        }
        else {


            //AbstractDungeon.effectList.add(new ThoughtBubble(AbstractDungeon.player.dialogX, AbstractDungeon.player.dialogY, 3.0F, TEXT[0], true));
        }


        this.isDone = true;
    }
}



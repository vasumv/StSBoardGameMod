package BoardGame.actions;

import BoardGame.cards.BGColorless.BGShieldSpear_Shield;
import BoardGame.cards.BGColorless.BGShieldSpear_Spear;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

import java.util.ArrayList;

//TODO: this works completely differently in multicharacter games -- no choice card, instead freely swap rows
//TODO: consider shield/spear animations for Facing abilities (might have to be different for multicharacter games)

public class BGSurroundedAction extends AbstractGameAction {

    private static final CardStrings SPEAR = CardCrawlGame.languagePack.getCardStrings("BoardGame:BGShieldSpear_Spear");
    private static final CardStrings SHIELD = CardCrawlGame.languagePack.getCardStrings("BoardGame:BGShieldSpear_Shield");

    @Override
    public void update() {
        isDone=true;
        if (BoardGame.multiplayer.MP.inRun && BoardGame.multiplayer.MP.playerCount() > 1) {
            // 3-4 players may switch rows first: wait until everyone has answered
            if (!BoardGame.multiplayer.combat.FacingVote.ready(facingText(BoardGame.multiplayer.MP.myRow()),
                    this::facingText)) {
                isDone = false;
                return;
            }
            partyFacing();
            return;
        }
        ArrayList<AbstractCard> choices = new ArrayList<>();
        {
            AbstractCard c = new BGShieldSpear_Shield();
            AbstractMonster shield=AbstractDungeon.getMonsters().monsters.get(1);
            //AbstractMonster m2=AbstractDungeon.getMonsters().monsters.get(0);
            c.name="Dodge Spear"; //TODO: localization
            if(shield.isDying || shield.isDead || shield.halfDead){
                c.rawDescription=SHIELD.EXTENDED_DESCRIPTION[3];
            }else {
                if (shield.nextMove == 0) {
                    c.rawDescription=SHIELD.EXTENDED_DESCRIPTION[0];
                } else if (shield.nextMove == 1) {
                    c.rawDescription=SHIELD.EXTENDED_DESCRIPTION[1];
                } else if (shield.nextMove == 2) {
                    c.rawDescription=SHIELD.EXTENDED_DESCRIPTION[2];
                }else{
                    c.rawDescription=SHIELD.EXTENDED_DESCRIPTION[0];
                }
            }
            c.initializeDescription();
            choices.add(c);
        }
        {
            AbstractCard c = new BGShieldSpear_Spear();
            AbstractMonster spear=AbstractDungeon.getMonsters().monsters.get(0);
            c.name="Dodge Shield"; //TODO: localization
            if(spear.isDying || spear.isDead || spear.halfDead){
                return;
            }else {
                c.rawDescription=SPEAR.EXTENDED_DESCRIPTION[0];
            }
            c.initializeDescription();
            choices.add(c);
        }
        AbstractDungeon.cardRewardScreen.chooseOneOpen(choices);
    }

    /**
     * Multiplayer (Spire Shield / Spire Spear cards): "After start of turn effects, players can
     * switch rows. Then they're immediately targeted by the Facing ability from the enemy in their
     * row." The Shield covers the bottom 2 rows and the Spear the top 2, so with 2 players everyone
     * faces both; with 3 the middle row faces both; with 4 the bottom two face the Shield and the
     * top two the Spear.
     */
    /** What a player in this row faces this turn, e.g. "Spire Shield (lose 1 Energy)". */
    private String facingText(int row) {
        int n = BoardGame.multiplayer.MP.playerCount();
        StringBuilder sb = new StringBuilder();
        for (AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
            if (m.isDeadOrEscaped() || m.halfDead) continue;
            if (m instanceof BoardGame.monsters.bgending.BGSpireShield && row <= 1 && sb.indexOf("Shield") < 0)
                sb.append(sb.length() > 0 ? " and " : "").append("Spire Shield (").append(m.nextMove == 1
                        ? "can't draw cards" : m.nextMove == 2 ? "your damage is 0" : "lose 1 Energy").append(")");
            if (m instanceof BoardGame.monsters.bgending.BGSpireSpear && row >= n - 2 && sb.indexOf("Spear") < 0)
                sb.append(sb.length() > 0 ? " and " : "").append("Spire Spear (2 Burns)");
        }
        return sb.length() == 0 ? "nothing" : sb.toString();
    }

    private void partyFacing() {
        int n = BoardGame.multiplayer.MP.playerCount();
        int row = BoardGame.multiplayer.MP.myRow();
        BoardGame.monsters.bgending.BGSpireShield shield = null;
        BoardGame.monsters.bgending.BGSpireSpear spear = null;
        for (AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
            if (m.isDeadOrEscaped() || m.halfDead) continue;
            if (m instanceof BoardGame.monsters.bgending.BGSpireShield && shield == null)
                shield = (BoardGame.monsters.bgending.BGSpireShield) m;
            if (m instanceof BoardGame.monsters.bgending.BGSpireSpear && spear == null)
                spear = (BoardGame.monsters.bgending.BGSpireSpear) m;
        }
        StringBuilder what = new StringBuilder();
        if (shield != null && row <= 1) {
            shield.facingAttack();
            what.append("Spire Shield (").append(shield.nextMove == 1 ? "you can't draw cards this turn"
                    : shield.nextMove == 2 ? "your damage is 0 this turn" : "lose 1 Energy").append(")");
        }
        if (spear != null && row >= n - 2) {
            spear.facingAttack();
            if (what.length() > 0) what.append(" and ");
            what.append("Spire Spear (2 Burns)");
        }
        if (what.length() > 0) BoardGame.multiplayer.Session.toast("Facing: " + what);
    }
}

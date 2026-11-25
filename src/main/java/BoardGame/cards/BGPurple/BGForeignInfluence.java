package BoardGame.cards.BGPurple;

import BoardGame.cards.AbstractBGCard;
import BoardGame.characters.BGWatcher;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class BGForeignInfluence extends AbstractBGCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("BoardGame:BGForeignInfluence");
    public static final String ID = "BGForeignInfluence";

    public BGForeignInfluence() {
        super("BGForeignInfluence", cardStrings.NAME, "purple/skill/foreign_influence", 2, cardStrings.DESCRIPTION, CardType.ATTACK, BGWatcher.Enums.BG_PURPLE, CardRarity.UNCOMMON, CardTarget.ENEMY);

        this.baseDamage=3;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        String other = BoardGame.multiplayer.combat.OtherPlayers.lastOtherAttack;
        if (BoardGame.multiplayer.combat.OtherPlayers.multi() && other != null) {
            final boolean up = BoardGame.multiplayer.combat.OtherPlayers.lastOtherAttackUpgraded;
            final String by = BoardGame.multiplayer.combat.OtherPlayers.lastOtherAttackBy;
            final AbstractCard copy = com.megacrit.cardcrawl.helpers.CardLibrary.getCopy(other, up ? 1 : 0, 0);
            java.util.ArrayList<String> opts = new java.util.ArrayList<>();
            opts.add("Deal " + this.damage + " damage");
            opts.add("Copy " + by + "'s " + (copy == null ? other : copy.name));
            final int dmg = this.damage;
            addToBot(BoardGame.multiplayer.ui.ChoicePrompt.action("Foreign Influence", opts, i -> {
                if (i == 1 && copy != null) {
                    copy.purgeOnUse = true;
                    copy.freeToPlayOnce = true;
                    com.megacrit.cardcrawl.dungeons.AbstractDungeon.actionManager.cardQueue.add(new com.megacrit.cardcrawl.cards.CardQueueItem(copy, m, 0, true, true));
                } else {
                    addToTop(new DamageAction(m, new DamageInfo(p, dmg, this.damageTypeForTurn), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
                }
            }));
            return;
        }
        addToBot((AbstractGameAction)new DamageAction((AbstractCreature)m, new DamageInfo((AbstractCreature)p, this.damage, this.damageTypeForTurn), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
    }


    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(1);
        }
    }

    public AbstractCard makeCopy() {
        return new BGForeignInfluence();
    }
}




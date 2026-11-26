package BoardGame.multiplayer.ui;

import BoardGame.dungeons.AbstractBGDungeon;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.defect.TriggerEndOfTurnOrbsAction;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.orbs.EmptyOrbSlot;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

import java.util.ArrayList;

/**
 * End of turn: orbs activate one at a time, in slot order, each one highlighted while it
 * resolves. (The base game fires them all at once, so they resolved last orb first.)
 */
public class OrbHighlight {
    @SpirePatch2(clz = TriggerEndOfTurnOrbsAction.class, method = "update")
    public static class InOrder {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(TriggerEndOfTurnOrbsAction __instance) {
            if (!(CardCrawlGame.dungeon instanceof AbstractBGDungeon)) return SpireReturn.Continue();
            ArrayList<AbstractOrb> orbs = new ArrayList<>();
            for (AbstractOrb o : AbstractDungeon.player.orbs) if (!(o instanceof EmptyOrbSlot)) orbs.add(o);
            // each orb's effects go on top of the queue, so add them last-to-first with a
            // highlight in front of each: they then resolve first-to-last
            for (int i = orbs.size() - 1; i >= 0; i--) {
                AbstractOrb o = orbs.get(i);
                o.onEndOfTurn();
                AbstractDungeon.actionManager.addToTop(new Show(o, i + 1, orbs.size()));
            }
            __instance.isDone = true;
            return SpireReturn.Return();
        }
    }

    /** Highlights one orb for a moment before its effect resolves. */
    public static class Show extends AbstractGameAction {
        private final AbstractOrb orb;
        private final String label;

        Show(AbstractOrb orb, int index, int count) {
            this(orb, orb.name + " (" + index + "/" + count + ")");
        }

        /** Highlight with a custom label, e.g. "Loop: Frost (x2)". */
        public Show(AbstractOrb orb, String label) {
            this.orb = orb;
            this.label = label;
            this.duration = this.startDuration = Settings.FAST_MODE ? 0.25f : 0.5f;
            this.actionType = ActionType.WAIT;
        }

        @Override
        public void update() {
            if (this.duration == this.startDuration)
                AbstractDungeon.topLevelEffects.add(new Ring(orb, label, this.startDuration + 0.25f));
            tickDuration();
        }
    }

    private static class Ring extends AbstractGameEffect {
        private final AbstractOrb orb;
        private final String label;

        Ring(AbstractOrb orb, String label, float duration) {
            this.orb = orb;
            this.label = label;
            this.duration = this.startingDuration = duration;
            this.color = new Color(1f, 0.85f, 0.3f, 1f);
        }

        @Override
        public void update() {
            this.duration -= Gdx.graphics.getDeltaTime();
            this.color.a = Math.min(1f, this.duration / 0.25f);
            if (this.duration < 0f) this.isDone = true;
        }

        @Override
        public void render(SpriteBatch sb) {
            float s = 96f * Settings.scale, t = 4f * Settings.scale;
            float x = orb.cX - s / 2f, y = orb.cY - s / 2f;
            sb.setColor(this.color);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, x, y, s, t);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, x, y + s - t, s, t);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, x, y, t, s);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, x + s - t, y, t, s);
            FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, label, orb.cX, orb.cY + s / 2f + 18f * Settings.scale,
                    this.color);
        }

        @Override
        public void dispose() {
        }
    }
}

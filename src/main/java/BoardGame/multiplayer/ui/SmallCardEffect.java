package BoardGame.multiplayer.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

/** A teammate's card, shown small just under their panel for a moment (not across the screen). */
public class SmallCardEffect extends AbstractGameEffect {
    private static final float SCALE = 0.4f;
    private final AbstractCard card;

    public SmallCardEffect(AbstractCard card, int playerId) {
        this.card = card;
        this.duration = this.startingDuration = Settings.FAST_MODE ? 1.0f : 1.6f;
        float[] r = RowView.panelOf(playerId);
        float ch = AbstractCard.IMG_HEIGHT * SCALE;
        if (r != null) {
            card.current_x = card.target_x = r[0] + r[2] - AbstractCard.IMG_WIDTH * SCALE / 2f - 8f * Settings.scale;
            card.current_y = card.target_y = r[1] - ch / 2f - 6f * Settings.scale;
        } else {
            card.current_x = card.target_x = Settings.WIDTH * 0.75f;
            card.current_y = card.target_y = Settings.HEIGHT * 0.65f;
        }
        card.drawScale = card.targetDrawScale = SCALE;
    }

    @Override
    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        card.transparency = card.targetTransparency = Math.min(1f, this.duration / 0.3f);
        if (this.duration < 0f) this.isDone = true;
    }

    @Override
    public void render(SpriteBatch sb) {
        card.render(sb);
    }

    @Override
    public void dispose() {
    }
}

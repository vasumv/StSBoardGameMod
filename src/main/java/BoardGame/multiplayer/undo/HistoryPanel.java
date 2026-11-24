package BoardGame.multiplayer.undo;

import basemod.interfaces.PostRenderSubscriber;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.badlogic.gdx.Gdx;

import java.util.List;

/**
 * Small combat overlay: a "History" button under the relic bar that opens the list of
 * steps resolved this turn. Clicking a step rolls back to just before it.
 */
public class HistoryPanel implements PostRenderSubscriber {
    private static final int MAX_ROWS = 12;
    private static final float X = 16f, TOP = 170f, W = 300f, ROW_H = 34f;
    private static final Color BG = new Color(0f, 0f, 0f, 0.65f);
    private static final Color BG_HOVER = new Color(0.25f, 0.35f, 0.45f, 0.9f);

    private static boolean open = false;
    private static boolean wasDown = false;

    public static String debug() {
        return "open=" + open + " hovered=" + button.hovered + " hb=" + button.x + "," + button.y + " " + button.width
                + "x" + button.height + " mouse=" + com.megacrit.cardcrawl.helpers.input.InputHelper.mX + ","
                + com.megacrit.cardcrawl.helpers.input.InputHelper.mY;
    }
    // sized in update(): Settings.scale is not known yet when this class loads
    private static final Hitbox button = new Hitbox(0f, 0f);
    private static final Hitbox[] rows = new Hitbox[MAX_ROWS];

    static {
        for (int i = 0; i < MAX_ROWS; i++) rows[i] = new Hitbox(0f, 0f);
    }

    private static void size(Hitbox hb) {
        hb.width = W * Settings.scale;
        hb.height = ROW_H * Settings.scale;
    }

    private static boolean visible() {
        return UndoManager.isIdle() || (open && !UndoManager.steps().isEmpty());
    }

    private static float rowY(int row) { // row 0 is the button
        return Settings.HEIGHT - (TOP + ROW_H * (row + 1)) * Settings.scale;
    }

    public static void update() {
        if (!UndoManager.isIdle()) {
            wasDown = Gdx.input.isButtonPressed(com.badlogic.gdx.Input.Buttons.LEFT);
            return;
        }
        List<UndoManager.Step> steps = UndoManager.steps();
        size(button);
        button.move(X * Settings.scale + button.width / 2f, rowY(0) + button.height / 2f);
        button.update();
        // runs after the game's own input pass has cleared its click flags, so detect the
        // press edge directly
        boolean down = Gdx.input.isButtonPressed(com.badlogic.gdx.Input.Buttons.LEFT);
        boolean clicked = down && !wasDown;
        wasDown = down;
        if (button.hovered && clicked) {
            open = !open;
            return;
        }
        if (!open) return;
        int first = Math.max(0, steps.size() - MAX_ROWS);
        for (int i = first; i < steps.size(); i++) {
            Hitbox hb = rows[i - first];
            size(hb);
            hb.move(X * Settings.scale + hb.width / 2f, rowY(i - first + 1) + hb.height / 2f);
            hb.update();
            if (hb.hovered && clicked) {
                UndoManager.rollbackTo(i);
                return;
            }
        }
    }

    @Override
    public void receivePostRender(SpriteBatch sb) {
        if (!visible()) return;
        List<UndoManager.Step> steps = UndoManager.steps();
        float x = X * Settings.scale, w = W * Settings.scale, h = ROW_H * Settings.scale;
        drawRow(sb, x, rowY(0), w, h, button.hovered,
                "History (" + steps.size() + ")" + (open ? "  - click a step to undo to before it" : ""),
                Settings.GOLD_COLOR);
        if (!open) return;
        int first = Math.max(0, steps.size() - MAX_ROWS);
        for (int i = first; i < steps.size(); i++) {
            boolean hov = rows[i - first].hovered;
            // hovering a step highlights it and everything after it: all of those get undone
            boolean affected = false;
            for (int j = first; j <= i; j++) affected |= rows[j - first].hovered;
            drawRow(sb, x, rowY(i - first + 1), w, h, hov || affected, (i + 1) + ". " + steps.get(i).label,
                    affected ? Settings.RED_TEXT_COLOR : Settings.CREAM_COLOR);
        }
    }

    private static void drawRow(SpriteBatch sb, float x, float y, float w, float h, boolean hovered, String text,
                                Color textColor) {
        sb.setColor(hovered ? BG_HOVER : BG);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x, y, w, h - 2f * Settings.scale);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, text, x + 8f * Settings.scale,
                y + h - 8f * Settings.scale, textColor);
        sb.setColor(Color.WHITE);
    }
}

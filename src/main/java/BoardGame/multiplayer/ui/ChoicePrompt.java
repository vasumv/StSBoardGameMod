package BoardGame.multiplayer.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.input.InputHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A small on-screen question with buttons ("Give Block to: You / Bob"). Used for
 * multiplayer choices that the single-player game never needed to ask.
 * {@link #action} gives an action that holds the action queue until the player answers.
 */
public class ChoicePrompt {
    private static String title;
    private static final ArrayList<String> options = new ArrayList<>();
    private static IntConsumer onPick;
    private static boolean wasDown = false;

    public static boolean open() {
        return onPick != null;
    }

    private static Object askedIn; // the room the question was asked in

    private static Object room() {
        try {
            return com.megacrit.cardcrawl.dungeons.AbstractDungeon.currMapNode == null ? null
                    : com.megacrit.cardcrawl.dungeons.AbstractDungeon.getCurrRoom();
        } catch (Throwable t) {
            return null;
        }
    }

    /** The party left the room: drop a question asked in an earlier room. */
    public static void roomChanged() {
        if (onPick != null && askedIn != room()) onPick = null;
    }

    /** Close the question only if it is this one (still the same answer handler). */
    public static void close(IntConsumer handler) {
        if (onPick == handler) onPick = null;
    }

    public static IntConsumer current() {
        return onPick;
    }

    public static void ask(String t, List<String> opts, IntConsumer pick) {
        title = t;
        askedIn = room();
        options.clear();
        options.addAll(opts);
        onPick = pick;
    }

    /** An action that asks the question and waits for the answer before the queue continues. */
    public static AbstractGameAction action(String t, List<String> opts, IntConsumer pick) {
        return new AbstractGameAction() {
            boolean asked = false;
            boolean answered = false;

            @Override
            public void update() {
                if (!asked) {
                    asked = true;
                    ask(t, opts, i -> {
                        answered = true;
                        pick.accept(i);
                    });
                }
                if (answered) isDone = true;
            }
        };
    }

    /**
     * Buttons sized to their text: side by side when they fit, otherwise stacked (long choices
     * like "Switch with Bob (row 2): Spire Spear (2 Burns)").
     */
    private static float[] box(int i) {
        float s = Settings.scale, h = 52 * s, gap = 16 * s;
        float[] w = new float[options.size()];
        float total = 0, widest = 0;
        for (int k = 0; k < options.size(); k++) {
            w[k] = Math.max(200 * s, FontHelper.getSmartWidth(FontHelper.tipBodyFont, options.get(k), 9999f, 0f) + 48 * s);
            total += w[k] + (k > 0 ? gap : 0);
            widest = Math.max(widest, w[k]);
        }
        float top = Settings.HEIGHT / 2f + 10 * s;
        if (total <= Settings.WIDTH * 0.8f) { // one row
            float x = Settings.WIDTH / 2f - total / 2f;
            for (int k = 0; k < i; k++) x += w[k] + gap;
            return new float[]{x, top - h, w[i], h};
        }
        // a column of equal-width buttons
        return new float[]{Settings.WIDTH / 2f - widest / 2f, top - h - i * (h + gap * 0.6f), widest, h};
    }

    /** The panel around the title and buttons: x, y, w, h. */
    private static float[] panel() {
        float s = Settings.scale;
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE;
        for (int k = 0; k < options.size(); k++) {
            float[] b = box(k);
            minX = Math.min(minX, b[0]);
            maxX = Math.max(maxX, b[0] + b[2]);
            minY = Math.min(minY, b[1]);
        }
        float titleW = FontHelper.getSmartWidth(FontHelper.tipBodyFont, title, 9999f, 0f) + 80 * s;
        float w = Math.max(Math.max(600 * s, titleW), maxX - minX + 60 * s);
        float top = Settings.HEIGHT / 2f + 80 * s;
        float bottom = minY - 24 * s;
        return new float[]{Settings.WIDTH / 2f - w / 2f, bottom, w, top - bottom};
    }

    public static void update() {
        boolean down = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        boolean clicked = down && !wasDown;
        wasDown = down;
        if (onPick == null || !clicked) return;
        for (int i = 0; i < options.size(); i++) {
            float[] b = box(i);
            if (InputHelper.mX >= b[0] && InputHelper.mX <= b[0] + b[2] && InputHelper.mY >= b[1]
                    && InputHelper.mY <= b[1] + b[3]) {
                IntConsumer p = onPick;
                onPick = null;
                InputHelper.justClickedLeft = false;
                p.accept(i);
                return;
            }
        }
    }

    public static void render(SpriteBatch sb) {
        if (onPick == null) return;
        float[] pr = panel();
        sb.setColor(new Color(0.05f, 0.07f, 0.1f, 0.94f));
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, pr[0], pr[1], pr[2], pr[3]);
        sb.setColor(new Color(0.85f, 0.7f, 0.3f, 0.6f)); // thin gold frame
        float t = 2 * Settings.scale;
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, pr[0], pr[1] + pr[3] - t, pr[2], t);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, pr[0], pr[1], pr[2], t);
        FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, title, Settings.WIDTH / 2f,
                pr[1] + pr[3] - 34 * Settings.scale, Settings.GOLD_COLOR);
        for (int i = 0; i < options.size(); i++) {
            float[] b = box(i);
            boolean hov = InputHelper.mX >= b[0] && InputHelper.mX <= b[0] + b[2] && InputHelper.mY >= b[1]
                    && InputHelper.mY <= b[1] + b[3];
            sb.setColor(hov ? new Color(0.3f, 0.4f, 0.5f, 1f) : new Color(0.18f, 0.22f, 0.28f, 1f));
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, b[0], b[1], b[2], b[3]);
            FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, options.get(i), b[0] + b[2] / 2f,
                    b[1] + b[3] / 2f, Settings.CREAM_COLOR);
        }
        sb.setColor(Color.WHITE);
    }
}

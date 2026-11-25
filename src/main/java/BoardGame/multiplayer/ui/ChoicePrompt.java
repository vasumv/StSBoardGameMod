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

    public static void ask(String t, List<String> opts, IntConsumer pick) {
        title = t;
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

    private static float[] box(int i) {
        float w = 260 * Settings.scale, h = 56 * Settings.scale, gap = 20 * Settings.scale;
        float total = options.size() * w + (options.size() - 1) * gap;
        return new float[]{Settings.WIDTH / 2f - total / 2f + i * (w + gap), Settings.HEIGHT / 2f - 40 * Settings.scale, w, h};
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
        float pw = Math.max(600 * Settings.scale, box(options.size() - 1)[0] + box(0)[2] - box(0)[0] + 60 * Settings.scale);
        float px = Settings.WIDTH / 2f - pw / 2f, py = Settings.HEIGHT / 2f - 80 * Settings.scale;
        sb.setColor(new Color(0.05f, 0.07f, 0.1f, 0.92f));
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, px, py, pw, 190 * Settings.scale);
        FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, title, Settings.WIDTH / 2f,
                py + 150 * Settings.scale, Settings.GOLD_COLOR);
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

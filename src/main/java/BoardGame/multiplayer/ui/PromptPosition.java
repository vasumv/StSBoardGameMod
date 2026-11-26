package BoardGame.multiplayer.ui;

import com.evacipated.cardcrawl.modthespire.lib.SpireInstrumentPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2;
import com.megacrit.cardcrawl.core.Settings;
import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

/**
 * The board game's choice prompts ("Choose a target...", "Loop: choose an Orb...") are drawn
 * where the teammate panels sit. In multiplayer, draw them in the free strip above the panels.
 */
public class PromptPosition {
    public static float y(float original) {
        return RowView.enabled() ? Settings.HEIGHT - 112f * Settings.scale : original;
    }

    private static ExprEditor edit() {
        return new ExprEditor() {
            @Override
            public void edit(MethodCall m) throws CannotCompileException {
                if (m.getMethodName().equals("renderFontCentered")
                        && m.getClassName().equals("com.megacrit.cardcrawl.helpers.FontHelper"))
                    m.replace("{ $5 = BoardGame.multiplayer.ui.PromptPosition.y($5); $_ = $proceed($$); }");
            }
        };
    }

    @SpirePatch2(clz = BoardGame.screen.TargetSelectScreen.class, method = "render")
    public static class Target {
        @SpireInstrumentPatch
        public static ExprEditor e() {
            return edit();
        }
    }

    @SpirePatch2(clz = BoardGame.screen.OrbSelectScreen.class, method = "render")
    public static class Orb {
        @SpireInstrumentPatch
        public static ExprEditor e() {
            return edit();
        }
    }

    @SpirePatch2(clz = BoardGame.screen.RelicTradingScreen.class, method = "render")
    public static class Relic {
        @SpireInstrumentPatch
        public static ExprEditor e() {
            return edit();
        }
    }
}

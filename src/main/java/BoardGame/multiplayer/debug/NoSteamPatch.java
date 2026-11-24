package BoardGame.multiplayer.debug;

import com.evacipated.cardcrawl.modthespire.lib.SpireInstrumentPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.NewExpr;

/**
 * Test-only: with -Dbg.nosteam=true, skip creating the Steam utilities object at startup.
 * Without a Steam client the game sometimes crashes natively there, which breaks
 * headless test instances. Has no effect for normal players.
 */
@SpirePatch2(clz = CardCrawlGame.class, method = "create")
public class NoSteamPatch {
    @SpireInstrumentPatch
    public static ExprEditor skipSteamUtils() {
        return new ExprEditor() {
            @Override
            public void edit(NewExpr e) throws CannotCompileException {
                if (e.getClassName().equals("com.codedisaster.steamworks.SteamUtils")) {
                    e.replace("if (Boolean.getBoolean(\"bg.nosteam\")) { $_ = null; } else { $_ = $proceed($$); }");
                }
            }
        };
    }
}

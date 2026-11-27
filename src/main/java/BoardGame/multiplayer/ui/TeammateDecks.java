package BoardGame.multiplayer.ui;

import BoardGame.multiplayer.MP;
import basemod.ReflectionHacks;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.screens.MasterDeckViewScreen;
import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

import java.util.HashMap;

/**
 * Teammates' decks in the deck viewer: tabs down the left side ("Your deck", then each
 * teammate). Picking a teammate shows their deck with the usual sorting and card popups.
 * Each game sends its deck with its status, so the view is always current.
 */
public class TeammateDecks {
    /** -1: your own deck; otherwise the player id being viewed. */
    public static int viewing = -1;
    private static final HashMap<Integer, CardGroup> built = new HashMap<>();
    private static final HashMap<Integer, String> builtFrom = new HashMap<>();

    /** This game's deck as text: card id and upgrade count. */
    public static String encode(AbstractPlayer p) {
        StringBuilder sb = new StringBuilder();
        for (AbstractCard c : p.masterDeck.group) {
            if (sb.length() > 0) sb.append('|');
            sb.append(c.cardID).append('#').append(c.timesUpgraded);
        }
        return sb.toString();
    }

    private static final HashMap<String, java.util.ArrayList<AbstractCard>> handCache = new HashMap<>();

    /** Cards from the "id#upgrades|..." text (cached). */
    public static java.util.ArrayList<AbstractCard> cards(String text) {
        if (text == null) return new java.util.ArrayList<>();
        java.util.ArrayList<AbstractCard> cached = handCache.get(text);
        if (cached != null) return cached;
        java.util.ArrayList<AbstractCard> out = new java.util.ArrayList<>();
        if (!text.isEmpty()) for (String part : text.split("\\|")) {
            int hash = part.lastIndexOf('#');
            AbstractCard c = CardLibrary.getCard(hash < 0 ? part : part.substring(0, hash));
            if (c == null) continue;
            c = c.makeCopy();
            int up = 0;
            try {
                up = hash < 0 ? 0 : Integer.parseInt(part.substring(hash + 1));
            } catch (NumberFormatException ignored) {
            }
            for (int i = 0; i < up && c.canUpgrade(); i++) c.upgrade();
            out.add(c);
        }
        if (handCache.size() > 50) handCache.clear();
        handCache.put(text, out);
        return out;
    }

    private static CardGroup groupFor(int id) {
        MP.Player pl = MP.player(id);
        if (pl == null || pl.deck == null) return null;
        if (pl.deck.equals(builtFrom.get(id))) return built.get(id);
        CardGroup g = new CardGroup(CardGroup.CardGroupType.MASTER_DECK);
        if (!pl.deck.isEmpty()) for (String part : pl.deck.split("\\|")) {
            int hash = part.lastIndexOf('#');
            String cid = hash < 0 ? part : part.substring(0, hash);
            int up = 0;
            try {
                up = hash < 0 ? 0 : Integer.parseInt(part.substring(hash + 1));
            } catch (NumberFormatException ignored) {
            }
            AbstractCard c = CardLibrary.getCard(cid);
            if (c == null) continue;
            c = c.makeCopy();
            for (int i = 0; i < up && c.canUpgrade(); i++) c.upgrade();
            g.addToTop(c);
        }
        built.put(id, g);
        builtFrom.put(id, pl.deck);
        return g;
    }

    /** What the deck viewer shows instead of player.masterDeck. */
    public static CardGroup group(AbstractPlayer p) {
        if (viewing < 0 || !MP.inRun) return p.masterDeck;
        CardGroup g = groupFor(viewing);
        return g == null ? p.masterDeck : g;
    }

    private static boolean multi() {
        return MP.inRun && MP.playerCount() > 1;
    }

    // ---- tabs ----

    private static final float TAB_X = 30f, TAB_W = 260f, TAB_H = 52f;

    private static float tabY(int i) {
        return Settings.HEIGHT - (260f + i * (TAB_H + 12f)) * Settings.scale;
    }

    private static boolean over(int i) {
        float x = TAB_X * Settings.scale, y = tabY(i);
        return InputHelper.mX >= x && InputHelper.mX <= x + TAB_W * Settings.scale && InputHelper.mY >= y
                && InputHelper.mY <= y + TAB_H * Settings.scale;
    }

    private static void select(MasterDeckViewScreen s, int id) {
        if (viewing == id) return;
        // put the old cards away and lay out the new ones
        ReflectionHacks.privateMethod(MasterDeckViewScreen.class, "hideCards").invoke(s);
        viewing = id;
        ReflectionHacks.setPrivate(s, MasterDeckViewScreen.class, "tmpSortedDeck", null);
        ReflectionHacks.privateMethod(MasterDeckViewScreen.class, "hideCards").invoke(s);
        ReflectionHacks.privateMethod(MasterDeckViewScreen.class, "calculateScrollBounds").invoke(s);
        ReflectionHacks.privateMethod(MasterDeckViewScreen.class, "resetScrolling").invoke(s);
    }

    @SpirePatch2(clz = MasterDeckViewScreen.class, method = "open")
    public static class OpenOnOwn {
        @SpirePrefixPatch
        public static void prefix() {
            viewing = -1;
        }
    }

    @SpirePatch2(clz = MasterDeckViewScreen.class, method = "update")
    public static class Tabs {
        @SpirePrefixPatch
        public static void prefix(MasterDeckViewScreen __instance) {
            if (!multi() || !InputHelper.justClickedLeft) return;
            int i = 0;
            if (over(i++)) {
                InputHelper.justClickedLeft = false;
                select(__instance, -1);
                return;
            }
            for (MP.Player p : MP.players) {
                if (p.id == MP.myId) continue;
                if (over(i++)) {
                    InputHelper.justClickedLeft = false;
                    select(__instance, p.id);
                    return;
                }
            }
        }
    }

    @SpirePatch2(clz = MasterDeckViewScreen.class, method = "render")
    public static class RenderTabs {
        @SpirePostfixPatch
        public static void postfix(SpriteBatch sb) {
            if (!multi()) return;
            int i = 0;
            tab(sb, i++, "Your deck (" + AbstractDungeon.player.masterDeck.size() + ")", viewing < 0);
            for (MP.Player p : MP.players) {
                if (p.id == MP.myId) continue;
                CardGroup g = groupFor(p.id);
                tab(sb, i++, p.name + (g == null ? "" : " (" + g.size() + ")"), viewing == p.id);
            }
        }

        private static void tab(SpriteBatch sb, int i, String label, boolean on) {
            float x = TAB_X * Settings.scale, y = tabY(i), w = TAB_W * Settings.scale, h = TAB_H * Settings.scale;
            sb.setColor(on ? new Color(0.25f, 0.45f, 0.25f, 0.95f)
                    : over(i) ? new Color(0.3f, 0.4f, 0.5f, 0.95f) : new Color(0.1f, 0.12f, 0.18f, 0.9f));
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, x, y, w, h);
            sb.setColor(Color.WHITE);
            FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, label, x + w / 2f, y + h / 2f,
                    Settings.CREAM_COLOR);
        }
    }

    /** Every read of player.masterDeck inside the deck viewer goes through group(). */
    private static ExprEditor redirect() {
        return new ExprEditor() {
            @Override
            public void edit(FieldAccess f) throws CannotCompileException {
                if (f.isReader() && f.getFieldName().equals("masterDeck")
                        && f.getClassName().equals(AbstractPlayer.class.getName()))
                    f.replace("$_ = " + TeammateDecks.class.getName() + ".group($0);");
            }
        };
    }

    @SpirePatch2(clz = MasterDeckViewScreen.class, method = "updateControllerInput")
    public static class R1 { @SpireInstrumentPatch public static ExprEditor e() { return redirect(); } }

    @SpirePatch2(clz = MasterDeckViewScreen.class, method = "updatePositions")
    public static class R2 { @SpireInstrumentPatch public static ExprEditor e() { return redirect(); } }

    @SpirePatch2(clz = MasterDeckViewScreen.class, method = "updateScrolling")
    public static class R3 { @SpireInstrumentPatch public static ExprEditor e() { return redirect(); } }

    @SpirePatch2(clz = MasterDeckViewScreen.class, method = "updateClicking")
    public static class R4 { @SpireInstrumentPatch public static ExprEditor e() { return redirect(); } }

    @SpirePatch2(clz = MasterDeckViewScreen.class, method = "calculateScrollBounds")
    public static class R5 { @SpireInstrumentPatch public static ExprEditor e() { return redirect(); } }

    @SpirePatch2(clz = MasterDeckViewScreen.class, method = "hideCards")
    public static class R6 { @SpireInstrumentPatch public static ExprEditor e() { return redirect(); } }

    @SpirePatch2(clz = MasterDeckViewScreen.class, method = "render")
    public static class R7 { @SpireInstrumentPatch public static ExprEditor e() { return redirect(); } }

    /** The line under the deck view: on a teammate's tab, say whose deck this is. */
    public static String tip(String original) {
        if (viewing < 0 || !MP.inRun) return original;
        MP.Player p = MP.player(viewing);
        return (p == null ? "A teammate" : p.name) + "'s deck (" + (p == null ? "" : MP.CHARACTER_NAMES[
                Math.max(0, Math.min(MP.CHARACTER_NAMES.length - 1, p.character))]) + ")";
    }

    @SpirePatch2(clz = MasterDeckViewScreen.class, method = "render")
    public static class TeammateTip {
        @com.evacipated.cardcrawl.modthespire.lib.SpireInstrumentPatch
        public static javassist.expr.ExprEditor edit() {
            return new javassist.expr.ExprEditor() {
                @Override
                public void edit(javassist.expr.MethodCall m) throws javassist.CannotCompileException {
                    if (m.getMethodName().equals("renderDeckViewTip"))
                        m.replace("{ $2 = BoardGame.multiplayer.ui.TeammateDecks.tip($2); $_ = $proceed($$); }");
                }
            };
        }
    }
}

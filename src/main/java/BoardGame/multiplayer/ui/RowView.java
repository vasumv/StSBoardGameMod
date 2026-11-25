package BoardGame.multiplayer.ui;

import BoardGame.characters.BGDefect;
import BoardGame.characters.BGIronclad;
import BoardGame.characters.BGSilent;
import BoardGame.characters.BGWatcher;
import BoardGame.multiplayer.MP;
import BoardGame.multiplayer.combat.MonsterSync;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.esotericsoftware.spine.Skeleton;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * How other players' rows look in a shared fight. Your own row is the normal battlefield.
 * Every other player gets a panel along the top: their character, HP and status, and the
 * enemies in their row drawn small. Those enemies can be targeted like any other.
 * Dragging an "any player" card onto a teammate's panel sends its effect to them.
 */
public class RowView {
    // panel layout in 1080p units, measured from the top-left corner
    private static final float PANEL_W = 450f, PANEL_H = 240f, PANEL_GAP = 16f, PANEL_LEFT = 470f, PANEL_TOP = 150f;
    private static final float SPRITE_SCALE = 0.38f;
    private static final Color PANEL_BG = new Color(0f, 0f, 0f, 0.45f);
    private static final Color PANEL_TARGET = new Color(0.2f, 0.8f, 0.3f, 0.35f);

    private static boolean rendering = false;
    private static final HashMap<Integer, AbstractPlayer> puppets = new HashMap<>();
    private static final HashMap<Integer, Integer> puppetChar = new HashMap<>();

    /** Teammates shown in panels, in roster order. */
    private static ArrayList<MP.Player> others() {
        ArrayList<MP.Player> l = new ArrayList<>();
        for (MP.Player p : MP.players) if (p.id != MP.myId) l.add(p);
        return l;
    }

    public static boolean enabled() {
        return MonsterSync.active();
    }

    private static int panelIndexForRow(int row) {
        ArrayList<MP.Player> o = others();
        for (int i = 0; i < o.size(); i++) if (o.get(i).row == row) return i;
        return -1;
    }

    /** Is this enemy shown small in a teammate's panel? */
    public static boolean inPanel(AbstractMonster m) {
        if (!enabled() || MonsterSync.isBossRoom()) return false;
        int r = MonsterSync.row(m);
        return r != MP.myRow() && panelIndexForRow(r) >= 0;
    }

    private static float s() {
        return Settings.scale;
    }

    /** Panel rectangle in screen pixels: x, y (bottom), w, h. */
    private static float[] panelRect(int i) {
        float x = (PANEL_LEFT + i * (PANEL_W + PANEL_GAP)) * s();
        float top = Settings.HEIGHT - PANEL_TOP * s();
        return new float[]{x, top - PANEL_H * s(), PANEL_W * s(), PANEL_H * s()};
    }

    private static float footY(float[] r) {
        return r[1] + 48f * s(); // room for the health bar under the sprite
    }

    /** Screen position (center x, foot y) of an enemy inside its row's panel. */
    private static float[] slotFor(AbstractMonster m) {
        int idx = panelIndexForRow(MonsterSync.row(m));
        float[] r = panelRect(idx);
        ArrayList<AbstractMonster> same = new ArrayList<>();
        for (AbstractMonster x : AbstractDungeon.getCurrRoom().monsters.monsters)
            if (MonsterSync.row(x) == MonsterSync.row(m)) same.add(x);
        int k = same.indexOf(m), n = Math.max(1, same.size());
        float left = r[0] + 125f * s(), right = r[0] + r[2] - 10f * s();
        float step = (right - left) / n;
        return new float[]{left + step * (k + 0.5f), footY(r), step};
    }

    // ---------------- enemy hitboxes follow the panel ----------------

    @SpirePatch2(clz = AbstractMonster.class, method = "update")
    public static class MonsterHitboxes {
        @SpirePostfixPatch
        public static void postfix(AbstractMonster __instance) {
            if (!inPanel(__instance)) return;
            float[] slot = slotFor(__instance);
            Hitbox hb = __instance.hb;
            float w = Math.min(__instance.hb_w * SPRITE_SCALE + 30f * s(), slot[2] - 12f * s());
            float h = Math.max(40f * s(), __instance.hb_h * SPRITE_SCALE);
            boolean changed = Math.abs(hb.width - w) > 0.5f;
            hb.width = w;
            hb.height = h;
            hb.move(slot[0], slot[1] + h / 2f);
            __instance.healthHb.width = w;
            __instance.healthHb.move(hb.cX, hb.y - __instance.healthHb.height / 2f);
            __instance.intentHb.move(hb.cX, hb.y + hb.height + 26f * s());
            if (changed) __instance.healthBarUpdatedEvent();
        }
    }

    // ---------------- drawing enemies small ----------------

    @SpirePatch2(clz = AbstractMonster.class, method = "render")
    public static class MonsterRender {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(AbstractMonster __instance, SpriteBatch sb) {
            if (rendering || !inPanel(__instance)) return SpireReturn.Continue();
            float[] slot = slotFor(__instance);
            renderSmall(__instance, sb, slot[0], slot[1], () -> __instance.render(sb));
            return SpireReturn.Return();
        }
    }

    private static Field skeletonField;

    private static Skeleton skeleton(AbstractCreature c) {
        try {
            if (skeletonField == null) {
                skeletonField = AbstractCreature.class.getDeclaredField("skeleton");
                skeletonField.setAccessible(true);
            }
            return (Skeleton) skeletonField.get(c);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Draw a creature at a smaller size at (x, foot) by temporarily moving and scaling it. */
    private static void renderSmall(AbstractCreature c, SpriteBatch sb, float x, float foot, Runnable draw) {
        float dx = c.drawX, dy = c.drawY, ax = c.animX, ay = c.animY;
        Skeleton sk = skeleton(c);
        float oldScale = Settings.scale;
        c.drawX = x;
        c.drawY = foot;
        c.animX *= SPRITE_SCALE;
        c.animY *= SPRITE_SCALE;
        com.badlogic.gdx.math.Matrix4 old = null;
        if (sk != null) {
            old = com.megacrit.cardcrawl.core.CardCrawlGame.psb.getTransformMatrix().cpy();
            com.badlogic.gdx.math.Matrix4 m = old.cpy().translate(x, foot, 0f).scale(SPRITE_SCALE, SPRITE_SCALE, 1f)
                    .translate(-x, -foot, 0f);
            com.megacrit.cardcrawl.core.CardCrawlGame.psb.setTransformMatrix(m);
        }
        rendering = true;
        try {
            if (sk == null) Settings.scale = oldScale * SPRITE_SCALE; // image-based creatures
            draw.run();
        } finally {
            Settings.scale = oldScale;
            rendering = false;
            if (old != null) com.megacrit.cardcrawl.core.CardCrawlGame.psb.setTransformMatrix(old);
            c.drawX = dx;
            c.drawY = dy;
            c.animX = ax;
            c.animY = ay;
        }
    }

    // ---------------- teammates ----------------

    private static AbstractPlayer puppet(MP.Player p) {
        AbstractPlayer a = puppets.get(p.id);
        Integer ch = puppetChar.get(p.id);
        if (a != null && ch != null && ch == p.character) return a;
        try {
            switch (p.character) {
                case 1: a = new BGSilent("The Silent", BGSilent.Enums.BG_SILENT); break;
                case 2: a = new BGDefect("The Defect", BGDefect.Enums.BG_DEFECT); break;
                case 3: a = new BGWatcher("The Watcher", BGWatcher.Enums.BG_WATCHER); break;
                default: a = new BGIronclad("The Ironclad", BGIronclad.Enums.BG_IRONCLAD); break;
            }
        } catch (Throwable t) {
            MP.logger.error("could not create teammate figure", t);
            return null;
        }
        puppets.put(p.id, a);
        puppetChar.put(p.id, p.character);
        return a;
    }

    public static void render(SpriteBatch sb) {
        if (!enabled()) return;
        ArrayList<MP.Player> o = others();
        int dragTarget = dragTargetId();
        for (int i = 0; i < o.size(); i++) {
            MP.Player p = o.get(i);
            float[] r = panelRect(i);
            sb.setColor(p.id == dragTarget ? PANEL_TARGET : PANEL_BG);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, r[0], r[1], r[2], r[3]);
            sb.setColor(Color.WHITE);
            String head = p.name + " (" + MP.CHARACTER_NAMES[p.character] + ")" + (p.endTurnReady ? "  READY" : "")
                    + (!p.connected ? "  (disconnected)" : "");
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, head, r[0] + 10f * s(),
                    r[1] + r[3] - 10f * s(), p.endTurnReady ? Settings.GREEN_TEXT_COLOR : Settings.GOLD_COLOR);
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, "Energy " + p.energy + "  Hand "
                    + p.handSize, r[0] + 10f * s(), r[1] + r[3] - 36f * s(), Settings.CREAM_COLOR);
            AbstractPlayer a = puppet(p);
            if (a == null) continue;
            float px = r[0] + 62f * s();
            float foot = footY(r);
            if (a.currentHealth != p.hp || a.maxHealth != p.maxHp || a.currentBlock != p.block) {
                a.maxHealth = Math.max(1, p.maxHp);
                a.currentHealth = p.hp;
                a.currentBlock = p.block;
                a.hb.width = 100f * s();
                a.hb.height = 150f * s() * SPRITE_SCALE * 2f;
                a.healthBarUpdatedEvent();
            }
            a.hb.width = 100f * s();
            a.hb.move(px, foot + a.hb.height / 2f);
            renderSmall(a, sb, px, foot, () -> a.renderPlayerImage(sb));
            a.renderHealth(sb);
        }
    }

    // ---------------- dragging "any player" cards onto teammates ----------------

    private static boolean givesToAnyPlayer(AbstractCard c) {
        return c != null && c.rawDescription != null && c.rawDescription.toLowerCase().contains("any player");
    }

    /** The teammate whose panel the dragged "any player" card is over, or -1. */
    public static int dragTargetId() {
        if (!enabled()) return -1;
        AbstractPlayer me = AbstractDungeon.player;
        if (me == null || !me.isDraggingCard || !givesToAnyPlayer(me.hoveredCard)) return -1;
        return panelUnderMouse();
    }

    private static int panelUnderMouse() {
        ArrayList<MP.Player> o = others();
        for (int i = 0; i < o.size(); i++) {
            float[] r = panelRect(i);
            float mx = InputHelper.mX, my = InputHelper.mY;
            if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) return o.get(i).id;
        }
        return -1;
    }

    /** Releasing over a teammate's panel counts as playing the card. */
    public static boolean dropOnTeammate(boolean original) {
        return original || dragTargetId() >= 0;
    }

    @SpirePatch2(clz = AbstractPlayer.class, method = "updateInput")
    public static class DropZone {
        @SpireInstrumentPatch
        public static ExprEditor edit() {
            return new ExprEditor() {
                @Override
                public void edit(FieldAccess f) throws CannotCompileException {
                    if (f.isWriter() && f.getFieldName().equals("isHoveringDropZone")) {
                        f.replace("{ $proceed(BoardGame.multiplayer.ui.RowView.dropOnTeammate($1)); }");
                    }
                }
            };
        }
    }

    /** Remember who a card was dropped on, for the play request. */
    @SpirePatch2(clz = AbstractPlayer.class, method = "playCard")
    public static class RememberRecipient {
        @SpirePrefixPatch
        public static void prefix() {
            BoardGame.multiplayer.Session.nextRecipient = dragTargetId();
        }
    }
}

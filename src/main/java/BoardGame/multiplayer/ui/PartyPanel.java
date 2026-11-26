package BoardGame.multiplayer.ui;

import BoardGame.multiplayer.MP;
import BoardGame.multiplayer.Session;
import BoardGame.multiplayer.combat.MonsterSync;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Out of combat: one "Party" button opens a panel with a card per teammate, where you can give
 * them gold (to help pay at a shop or event), hand over a potion, or switch rows with them.
 */
public class PartyPanel {
    public static boolean open = false;
    private static final HashMap<Integer, Integer> goldAmount = new HashMap<>();

    // layout in 1080p units from the top-left
    private static final float PX = 300, PY = 170, PW = 1320, PH = 640;
    private static final float CARD_W = 400, CARD_GAP = 30;

    private static final Color SHADE = new Color(0f, 0f, 0f, 0.55f);
    private static final Color PANEL = new Color(0.07f, 0.09f, 0.12f, 0.97f);
    private static final Color CARD = new Color(0.13f, 0.16f, 0.21f, 1f);
    private static final Color BTN = new Color(0.2f, 0.27f, 0.35f, 1f);
    private static final Color BTN_HOVER = new Color(0.32f, 0.45f, 0.58f, 1f);
    private static final Color BTN_OFF = new Color(0.16f, 0.17f, 0.19f, 1f);

    /** The panel and its button are offered out of combat, when nothing else covers the screen. */
    public static boolean available() {
        if (!MP.inRun || MP.playerCount() < 2 || !CardCrawlGame.isInARun() || AbstractDungeon.player == null
                || AbstractDungeon.currMapNode == null || AbstractDungeon.getCurrRoom() == null) return false;
        if (MonsterSync.active() || AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT) return false;
        return true;
    }

    private static boolean goldUseful() {
        AbstractRoom r = AbstractDungeon.getCurrRoom();
        return r instanceof com.megacrit.cardcrawl.rooms.ShopRoom || r instanceof com.megacrit.cardcrawl.rooms.EventRoom;
    }

    private static ArrayList<MP.Player> teammates() {
        ArrayList<MP.Player> o = new ArrayList<>();
        for (MP.Player p : MP.players) if (p.id != MP.myId) o.add(p);
        return o;
    }

    // ---------------- buttons: collected while drawing, clicked in update ----------------

    private static class Btn {
        float x, y, w, h;
        String text;
        boolean enabled;
        Runnable act;
    }

    private static final ArrayList<Btn> btns = new ArrayList<>();

    private static void btn(SpriteBatch sb, float x, float y, float w, float h, String text, boolean enabled, Runnable act) {
        Btn b = new Btn();
        b.x = x;
        b.y = y;
        b.w = w;
        b.h = h;
        b.text = text;
        b.enabled = enabled;
        b.act = act;
        btns.add(b);
        if (sb == null) return;
        rect(sb, x, y, w, h, !enabled ? BTN_OFF : hovered(b) ? BTN_HOVER : BTN);
        FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, text, sx(x + w / 2), sy(y + h / 2),
                enabled ? Settings.GOLD_COLOR : Color.GRAY);
    }

    private static float sx(float x) {
        return x * Settings.scale;
    }

    private static float sy(float y) {
        return Settings.HEIGHT - y * Settings.scale;
    }

    private static void rect(SpriteBatch sb, float x, float y, float w, float h, Color c) {
        sb.setColor(c);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, sx(x), sy(y + h), w * Settings.scale, h * Settings.scale);
        sb.setColor(Color.WHITE);
    }

    private static boolean hovered(Btn b) {
        float mx = InputHelper.mX, my = InputHelper.mY;
        return mx >= sx(b.x) && mx <= sx(b.x + b.w) && my >= sy(b.y + b.h) && my <= sy(b.y);
    }

    private static void text(SpriteBatch sb, String t, float x, float y, Color c) {
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, t, sx(x), sy(y), c);
    }

    // ---------------- frame ----------------

    /** Called every frame (after the game's own update). */
    private static Object openedIn;

    public static void update() {
        if (!available()) {
            open = false;
            return;
        }
        if (open && openedIn != AbstractDungeon.getCurrRoom()) open = false; // a new room closes it
        if (!open) openedIn = AbstractDungeon.getCurrRoom();
        if (!MPOverlay.peekClick()) return;
        for (Btn b : new ArrayList<>(btns)) {
            if (b.enabled && hovered(b)) {
                MPOverlay.takeClick();
                b.act.run();
                return;
            }
        }
        if (open && MPOverlay.takeClick()) {
            // a click outside the panel closes it
            float mx = InputHelper.mX, my = InputHelper.mY;
            if (mx < sx(PX) || mx > sx(PX + PW) || my > sy(PY) || my < sy(PY + PH)) open = false;
        }
    }

    public static void render(SpriteBatch sb) {
        btns.clear();
        if (!available()) return;
        // the opener sits where the old party buttons were, under the relic bar
        boolean screenFree = !AbstractDungeon.isScreenUp || AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP;
        if (!open) {
            if (screenFree) btn(sb, 16, 170, 200, 40, "Party", true, () -> open = true);
            return;
        }
        sb.setColor(SHADE);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0, 0, Settings.WIDTH, Settings.HEIGHT);
        sb.setColor(Color.WHITE);
        rect(sb, PX, PY, PW, PH, PANEL);
        FontHelper.renderFontCentered(sb, FontHelper.bannerNameFont, "Party", sx(PX + PW / 2), sy(PY + 40),
                Settings.GOLD_COLOR);
        text(sb, "You have " + AbstractDungeon.player.gold + " gold"
                + (goldUseful() ? "" : "   (gold can be given in a shop or an event, to help pay)"), PX + 30, PY + 80,
                Settings.CREAM_COLOR);
        ArrayList<MP.Player> mates = teammates();
        float totalW = mates.size() * CARD_W + (mates.size() - 1) * CARD_GAP;
        float x0 = PX + (PW - totalW) / 2;
        for (int i = 0; i < mates.size(); i++) renderCard(sb, mates.get(i), x0 + i * (CARD_W + CARD_GAP), PY + 120);
        btn(sb, PX + PW - 190, PY + PH - 64, 160, 46, "Close", true, () -> open = false);
    }

    private static void renderCard(SpriteBatch sb, MP.Player p, float x, float y) {
        float h = 480;
        rect(sb, x, y, CARD_W, h, CARD);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipHeaderFont, p.name, sx(x + 16), sy(y + 16),
                p.connected ? Settings.GOLD_COLOR : Color.GRAY);
        text(sb, MP.CHARACTER_NAMES[Math.max(0, Math.min(MP.CHARACTER_NAMES.length - 1, p.character))] + ",  row "
                + (p.row + 1) + (p.connected ? "" : "  (disconnected)"), x + 16, y + 50, Settings.CREAM_COLOR);
        text(sb, "HP " + p.hp + "/" + p.maxHp + "      Gold " + p.gold, x + 16, y + 78, Settings.CREAM_COLOR);
        boolean can = p.connected && !p.dead;
        float cy = y + 120;

        // gold
        text(sb, "Give gold", x + 16, cy, Settings.GOLD_COLOR);
        cy += 30;
        int mine = AbstractDungeon.player.gold;
        int amt = Math.max(1, Math.min(goldAmount.getOrDefault(p.id, 1), Math.max(1, mine)));
        goldAmount.put(p.id, amt);
        boolean goldOk = can && goldUseful() && mine > 0;
        final int id = p.id;
        btn(sb, x + 16, cy, 50, 44, "-", goldOk && amt > 1, () -> goldAmount.put(id, amt - 1));
        if (sb != null) FontHelper.renderFontCentered(sb, FontHelper.tipHeaderFont, String.valueOf(amt),
                sx(x + 101), sy(cy + 22), goldOk ? Settings.CREAM_COLOR : Color.GRAY);
        btn(sb, x + 136, cy, 50, 44, "+", goldOk && amt < mine, () -> goldAmount.put(id, amt + 1));
        btn(sb, x + 200, cy, CARD_W - 216, 44, "Give " + amt + " gold", goldOk, () -> {
            Session.giveGold(amt, id);
            goldAmount.put(id, 1);
        });
        cy += 64;

        // potions
        text(sb, "Give a potion", x + 16, cy, Settings.GOLD_COLOR);
        cy += 30;
        boolean any = false;
        for (int i = 0; i < AbstractDungeon.player.potions.size(); i++) {
            AbstractPotion po = AbstractDungeon.player.potions.get(i);
            if (po instanceof PotionSlot) continue;
            any = true;
            final int slot = i;
            btn(sb, x + 16, cy, CARD_W - 32, 40, po.name, can, () -> Session.givePotion(slot, id));
            cy += 46;
        }
        if (!any) {
            text(sb, "(you have no potions)", x + 16, cy + 6, Color.GRAY);
            cy += 46;
        }
        cy += 18;

        // rows
        btn(sb, x + 16, y + h - 60, CARD_W - 32, 44, "Switch rows with " + p.name, can, () -> Session.swapRow(id));
    }

    /**
     * Gold from a teammate: an event that locked the options you couldn't afford is rebuilt (while
     * you're still on its first screen) so the ones you can afford now unlock. Only events whose
     * options don't depend on randomness are rebuilt.
     */
    public static void goldChanged() {
        try {
            if (!CardCrawlGame.isInARun() || AbstractDungeon.currMapNode == null) return;
            AbstractRoom r = AbstractDungeon.getCurrRoom();
            if (!(r instanceof com.megacrit.cardcrawl.rooms.EventRoom) || r.event == null) return;
            com.megacrit.cardcrawl.events.AbstractEvent ev = r.event;
            String n = ev.getClass().getSimpleName();
            if (ev instanceof BoardGame.events.BGTheJoust) {
                ((BoardGame.events.BGTheJoust) ev).goldChanged();
                return;
            }
            if (!n.equals("BGCleric") && !n.equals("BGBeggar") && !n.equals("BGWomanInBlue")) return;
            if (!atIntro(ev)) return;
            com.megacrit.cardcrawl.events.AbstractEvent fresh = ev.getClass().getConstructor().newInstance();
            r.event = fresh;
            fresh.onEnterRoom();
        } catch (Throwable t) {
            MP.logger.error("could not refresh the event after receiving gold", t);
        }
    }

    private static boolean atIntro(com.megacrit.cardcrawl.events.AbstractEvent ev) {
        for (String f : new String[]{"screen", "screenNum"}) {
            for (Class<?> c = ev.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    java.lang.reflect.Field fl = c.getDeclaredField(f);
                    fl.setAccessible(true);
                    Object v = fl.get(ev);
                    return "INTRO".equals(String.valueOf(v)) || Integer.valueOf(0).equals(v);
                } catch (NoSuchFieldException ignored) {
                } catch (Throwable t) {
                    return false;
                }
            }
        }
        return false;
    }

    /** While the panel is open, the room underneath ignores clicks. */
    @SpirePatch2(clz = AbstractDungeon.class, method = "update")
    public static class BlockClicks {
        @SpirePrefixPatch
        public static void prefix() {
            if (open) {
                InputHelper.justClickedLeft = false;
                InputHelper.justReleasedClickLeft = false;
                InputHelper.justClickedRight = false;
            }
        }
    }
}

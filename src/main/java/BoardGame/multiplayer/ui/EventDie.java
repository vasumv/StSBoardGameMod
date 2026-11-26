package BoardGame.multiplayer.ui;

import BoardGame.multiplayer.MP;
import BoardGame.multiplayer.Session;
import BoardGame.potions.BGGamblersBrew;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.rooms.EventRoom;
import com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.function.IntConsumer;

/**
 * The die in events.
 * <p>
 * Personal rolls (The Joust, Wheel of Change, Scrap Ooze...) are shown rolling, with a line for each
 * relic that changed the number.
 * <p>
 * Party rolls (Lab, Dead Adventurer) are one die for the whole party, decided like the combat die:
 * everyone sees the roll, anyone may change it with their own Abacus, Toolbox, Gambling Chip or
 * Gambler's Brew (everyone sees the change and confirms again), and once everyone has kept the
 * number the event resolves on it in every game. Players with nothing that can change the die keep
 * it automatically.
 */
public class EventDie {
    // what's shown
    private static int key = -1;      // room the die belongs to
    private static int value = 0;     // 1-6
    private static float anim = 0f;   // seconds of rolling animation left
    private static String title = "";
    private static final ArrayList<String> notes = new ArrayList<>();
    private static int offset = 0;    // Wheel of Change rolls 0-5

    // the party decision
    private static boolean party = false, resolved = false;
    private static int version = 0;
    private static final HashSet<Integer> locks = new HashSet<>();
    private static IntConsumer onResolve;
    private static boolean usedAbacus, usedToolbox, usedChip, choosingBrew;
    private static int autoLockedVersion = -1;
    private static final ArrayList<JsonObject> early = new ArrayList<>(); // messages that beat our own start

    private static boolean inEvent() {
        return CardCrawlGame.isInARun() && AbstractDungeon.currMapNode != null
                && AbstractDungeon.getCurrRoom() instanceof EventRoom && AbstractDungeon.getCurrRoom().event != null;
    }

    private static int roomKey() {
        return AbstractDungeon.actNum * 1000 + AbstractDungeon.floorNum;
    }

    private static boolean current() {
        return inEvent() && key == roomKey();
    }

    private static void fresh(String what) {
        if (key != roomKey()) {
            key = roomKey();
            party = false;
            resolved = false;
            version = 0;
            locks.clear();
            onResolve = null;
            usedAbacus = usedToolbox = usedChip = choosingBrew = false;
            autoLockedVersion = -1;
            notes.clear();
            value = 0;
        }
        if (what != null) title = what;
    }

    // ---------------- personal rolls ----------------

    /** An event rolled the die (lo = 0 for the 0-5 Wheel of Change). */
    public static void rolled(int v, int lo, int hi) {
        if (!inEvent() || hi - lo != 5) return;
        boolean reroll = key == roomKey() && value > 0; // a second roll in the same event
        fresh(null);
        if (party) return; // the party die shows its own changes
        offset = lo == 0 ? 1 : 0;
        if (reroll) notes.add("Rerolled: " + (v + offset));
        else {
            notes.clear();
            title = eventName();
        }
        value = v + offset;
        anim = 0.9f;
    }

    /** An event changed its roll (Abacus, Toolbox, Gambler's Brew...). */
    public static void changed(int v) {
        if (!current() || party || value <= 0 || v + offset == value || v + offset < 1 || v + offset > 6) return;
        notes.add("Changed " + value + " to " + (v + offset));
        value = v + offset;
        anim = Math.max(anim, 0.4f);
    }

    /** A die relic was used automatically on an event roll. */
    public static void relicUsed(String relicId) {
        if (!current() || party || value <= 0) return;
        switch (relicId) {
            case "BGTheAbacus":
                notes.add("The Abacus: " + value + " becomes " + (value % 6 + 1));
                value = value % 6 + 1;
                anim = Math.max(anim, 0.4f);
                break;
            case "BGToolbox":
                notes.add("Toolbox: " + value + " becomes " + (value == 1 ? 6 : value - 1));
                value = value == 1 ? 6 : value - 1;
                anim = Math.max(anim, 0.4f);
                break;
            case "BGGambling Chip":
                notes.add("Gambling Chip: reroll the " + value);
                break;
        }
    }

    /** What each number does in this event: {lowest, highest, text}. */
    private static ArrayList<Object[]> outcomes() {
        ArrayList<Object[]> o = new ArrayList<>();
        AbstractEvent ev = AbstractDungeon.getCurrRoom().event;
        String n = ev.getClass().getSimpleName();
        switch (n) {
            case "BGLab": {
                MP.Player bottom = multi() ? MP.playerInRow(0) : null;
                String who = bottom == null || bottom.id == MP.myId ? "you" : bottom.name;
                o.add(new Object[]{1, 3, "One potion each"});
                o.add(new Object[]{4, 6, multi() ? "One potion each, and " + who + " gets an extra potion"
                        : "Two potions"});
                break;
            }
            case "BGDeadAdventurer":
                o.add(new Object[]{1, 2, "Fight an Elite (it drops 2 Gold and a Relic)"});
                o.add(new Object[]{3, 4, multi() ? "Everyone gains 2 Gold" : "Gain 2 Gold"});
                o.add(new Object[]{5, 6, multi() ? "Everyone obtains a Relic" : "Obtain a Relic"});
                break;
            case "BGTheJoust":
                o.add(new Object[]{1, 3, "Lose your bet"});
                o.add(new Object[]{4, 6, "Win 6 Gold"});
                break;
            case "BGTheMausoleum":
            case "BGAccursedBlacksmith":
                o.add(new Object[]{1, 3, "Keep the Relic, and gain a Curse"});
                o.add(new Object[]{4, 6, "Keep the Relic, no Curse"});
                break;
            case "BGScrapOoze":
                o.add(new Object[]{1, 2, "Nothing: reach in again (lose HP) or leave"});
                o.add(new Object[]{3, 4, "Find 2 Gold"});
                o.add(new Object[]{5, 6, "Find a Relic"});
                break;
        }
        return o;
    }

    private static String eventName() {
        AbstractEvent ev = AbstractDungeon.getCurrRoom().event;
        String n = ev.getClass().getSimpleName().replaceFirst("^BG", "").replaceAll("([a-z])([A-Z])", "$1 $2");
        return n + ": die roll";
    }

    // ---------------- party rolls ----------------

    public static boolean multi() {
        return MP.inRun && MP.playerCount() > 1;
    }

    /** True once this room's party die has been decided. */
    public static boolean decided() {
        return current() && party && resolved;
    }

    public static boolean partyStarted() {
        return current() && party;
    }

    public static int value() {
        return value;
    }

    /**
     * Every game starts the same party roll (same number from the shared seed); the event goes on
     * in resolve once everyone has kept the final number.
     */
    public static void startParty(String what, int v, IntConsumer resolve) {
        if (!inEvent()) return;
        fresh(what);
        if (party) return;
        party = true;
        resolved = false;
        offset = 0;
        value = v;
        anim = 0.9f;
        notes.clear();
        onResolve = resolve;
        ArrayList<JsonObject> pending = new ArrayList<>(early);
        early.clear();
        for (JsonObject m : pending) onMessage(m.get("t").getAsString(), m.get("from").getAsInt(), m);
        maybeResolve();
    }

    private static boolean hasAbacus() {
        return !usedAbacus && AbstractDungeon.player.hasRelic("BGTheAbacus");
    }

    private static boolean hasToolbox() {
        return !usedToolbox && AbstractDungeon.player.hasRelic("BGToolbox");
    }

    private static boolean hasChip() {
        return !usedChip && AbstractDungeon.player.hasRelic("BGGambling Chip");
    }

    private static boolean hasBrew() {
        return BGGamblersBrew.doesPlayerHaveGamblersBrew() > -1;
    }

    private static boolean canChange() {
        return hasAbacus() || hasToolbox() || hasChip() || hasBrew();
    }

    private static void change(int v, String how) {
        JsonObject o = MP.msg("edie");
        o.addProperty("k", key);
        o.addProperty("v", v);
        o.addProperty("how", how);
        MP.send(o);
    }

    private static void lock() {
        if (locks.contains(MP.myId)) return;
        JsonObject o = MP.msg("elock");
        o.addProperty("k", key);
        o.addProperty("ver", version);
        MP.send(o);
    }

    public static void onMessage(String type, int from, JsonObject m) {
        int k = m.get("k").getAsInt();
        if (inEvent() && k == roomKey() && !(key == k && party)) {
            early.add(m); // ours hasn't started yet
            return;
        }
        if (!current() || !party || k != key || resolved) return;
        if (type.equals("edie")) {
            int v = m.get("v").getAsInt();
            version++;
            locks.clear();
            notes.add(Session.nameOf(from) + ": " + m.get("how").getAsString() + ": " + value + " becomes " + v);
            value = v;
            anim = 0.6f;
        } else if (type.equals("elock")) {
            if (m.get("ver").getAsInt() != version) return; // kept an older number
            locks.add(from);
            maybeResolve();
        }
    }

    private static void maybeResolve() {
        if (!party || resolved) return;
        for (MP.Player p : MP.players) if (p.connected && !p.dead && !locks.contains(p.id)) return;
        resolved = true;
        choosingBrew = false;
        releaseButtons();
        notes.add("Final: " + value);
        if (onResolve != null) {
            IntConsumer r = onResolve;
            onResolve = null;
            r.accept(value);
        }
    }

    private static String waitingFor() {
        StringBuilder sb = new StringBuilder();
        for (MP.Player p : MP.players) {
            if (!p.connected || p.dead || locks.contains(p.id)) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(p.id == MP.myId ? "you" : p.name);
        }
        return sb.toString();
    }

    // ---------------- frame ----------------

    private static final ArrayList<float[]> btnRects = new ArrayList<>();
    private static final ArrayList<String> btnText = new ArrayList<>();
    private static final ArrayList<Runnable> btnAct = new ArrayList<>();

    public static void update() {
        if (anim > 0) anim -= Gdx.graphics.getDeltaTime();
        if (!current()) {
            if (!inEvent()) early.clear();
            return;
        }
        if (party && !resolved) {
            // while the die is being decided the event's own buttons wait
            for (LargeDialogOptionButton b : AbstractDungeon.getCurrRoom().event.imageEventText.optionList)
                b.isDisabled = true;
            for (LargeDialogOptionButton b : com.megacrit.cardcrawl.events.RoomEventDialog.optionList)
                b.isDisabled = true;
            if (anim <= 0 && !locks.contains(MP.myId) && autoLockedVersion != version && !canChange()) {
                autoLockedVersion = version;
                lock();
            }
            maybeResolve(); // someone may have left
        }
        layoutButtons();
        if (anim > 0 || btnRects.isEmpty()) return;
        for (int i = 0; i < btnRects.size(); i++) {
            float[] r = btnRects.get(i);
            if (hovered(r) && InputHelper.justClickedLeft) {
                InputHelper.justClickedLeft = false;
                btnAct.get(i).run();
                return;
            }
        }
    }

    /** Gives the event's buttons back once the die is decided (called by the events). */
    public static void releaseButtons() {
        for (LargeDialogOptionButton b : AbstractDungeon.getCurrRoom().event.imageEventText.optionList)
            b.isDisabled = false;
        for (LargeDialogOptionButton b : com.megacrit.cardcrawl.events.RoomEventDialog.optionList)
            b.isDisabled = false;
    }

    // panel geometry, 1080p units from the top-left
    private static float px() {
        // image events: over the picture; room events (Dead Adventurer): the empty right side
        return Boolean.TRUE.equals(basemod.ReflectionHacks.getPrivateStatic(com.megacrit.cardcrawl.events.GenericEventDialog.class, "show")) ? 200 : 1180;
    }

    private static final float PY = 380, PW = 600;

    private static void layoutButtons() {
        btnRects.clear();
        btnText.clear();
        btnAct.clear();
        if (!party || resolved || anim > 0) return;
        float y = PY + 150 + (outcomes().size() + notes.size()) * 26;
        if (choosingBrew) {
            for (int i = 1; i <= 6; i++) {
                final int v = i;
                addBtn(px() + 12 + (i - 1) * 96, y, 86, "" + i, () -> {
                    int slot = BGGamblersBrew.doesPlayerHaveGamblersBrew();
                    choosingBrew = false;
                    if (slot < 0) return;
                    AbstractDungeon.topPanel.destroyPotion(slot);
                    change(v, "Gambler's Brew");
                });
            }
            return;
        }
        if (locks.contains(MP.myId)) return;
        float x = px() + 12;
        if (hasAbacus()) {
            addBtn(x, y, 300, "The Abacus: make it " + (value % 6 + 1), () -> {
                usedAbacus = true;
                change(value % 6 + 1, "The Abacus");
            });
            y += 46;
        }
        if (hasToolbox()) {
            addBtn(x, y, 300, "Toolbox: make it " + (value == 1 ? 6 : value - 1), () -> {
                usedToolbox = true;
                change(value == 1 ? 6 : value - 1, "Toolbox");
            });
            y += 46;
        }
        if (hasChip()) {
            addBtn(x, y, 300, "Gambling Chip: reroll", () -> {
                usedChip = true;
                change(new java.util.Random(MP.seed * 31L + key * 7919L + version * 104729L).nextInt(6) + 1,
                        "Gambling Chip");
            });
            y += 46;
        }
        if (hasBrew()) {
            addBtn(x, y, 300, "Gambler's Brew: pick a number", () -> choosingBrew = true);
            y += 46;
        }
        addBtn(px() + PW - 200, PY + 150 + (outcomes().size() + notes.size()) * 26, 188, "Keep " + value,
                EventDie::lock);
    }

    private static void addBtn(float x, float y, float w, String text, Runnable r) {
        btnRects.add(new float[]{x * Settings.scale, Settings.HEIGHT - (y + 38) * Settings.scale,
                w * Settings.scale, 38 * Settings.scale});
        btnText.add(text);
        btnAct.add(r);
    }

    private static boolean hovered(float[] r) {
        return InputHelper.mX >= r[0] && InputHelper.mX <= r[0] + r[2] && InputHelper.mY >= r[1]
                && InputHelper.mY <= r[1] + r[3];
    }

    private static final Color BG = new Color(0f, 0f, 0f, 0.82f);
    private static final Color BTN = new Color(0.12f, 0.16f, 0.2f, 0.95f);
    private static final Color BTN_HOVER = new Color(0.25f, 0.35f, 0.45f, 0.95f);
    private static final java.util.Random animRng = new java.util.Random();
    private static int face = 1;
    private static float faceTick = 0;

    public static void render(SpriteBatch sb) {
        if (!current() || value <= 0 || AbstractDungeon.isScreenUp) return;
        float s = Settings.scale;
        boolean rolling = anim > 0;
        if (rolling) {
            faceTick -= Gdx.graphics.getDeltaTime();
            if (faceTick <= 0) {
                face = animRng.nextInt(6) + 1;
                faceTick = 0.07f;
            }
        }
        int extraRows = 0;
        if (party && !resolved && !rolling && !locks.contains(MP.myId))
            extraRows = Math.max(1, (hasAbacus() ? 1 : 0) + (hasToolbox() ? 1 : 0) + (hasChip() ? 1 : 0)
                    + (hasBrew() ? 1 : 0));
        if (choosingBrew) extraRows = 1;
        ArrayList<Object[]> outs = outcomes();
        float h = 140 + (outs.size() + notes.size()) * 26 + (party && !resolved ? 30 : 0) + extraRows * 46 + 10;
        float x = px() * s, top = Settings.HEIGHT - PY * s;
        box(sb, x, top - h * s, PW * s, h * s, BG);
        float ds = 96 * s;
        float dx = x + 16 * s, dy = top - 16 * s - ds;
        box(sb, dx, dy, ds, ds, rolling ? Color.LIGHT_GRAY : Color.WHITE);
        FontHelper.renderFontCentered(sb, FontHelper.bannerNameFont, String.valueOf(rolling ? face : value),
                dx + ds / 2f, dy + ds / 2f, Color.BLACK);
        String head = party ? (resolved ? "Party die: " + value : rolling ? "Rolling the party die..."
                : "Party die: " + value + " - keep it or change it") : rolling ? "Rolling..." : "Rolled " + value;
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipHeaderFont, head, x + 128 * s, top - 22 * s,
                party && !resolved ? Settings.GOLD_COLOR : Settings.GREEN_TEXT_COLOR);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, title, x + 128 * s, top - 58 * s,
                Settings.CREAM_COLOR);
        float y = top - 140 * s;
        for (Object[] r : outs) {
            int lo = (Integer) r[0], hi = (Integer) r[1];
            boolean hit = !rolling && value >= lo && value <= hi;
            String range = lo == hi ? "" + lo : lo + "-" + hi;
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, (hit ? "> " : "   ") + range + ":  " + r[2],
                    x + 16 * s, y, hit ? Settings.GREEN_TEXT_COLOR : Color.GRAY);
            y -= 26 * s;
        }
        if (!outs.isEmpty()) y -= 4 * s;
        if (!rolling) {
            for (String n : notes) {
                FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, n, x + 16 * s, y, Settings.CREAM_COLOR);
                y -= 26 * s;
            }
        }
        if (party && !resolved && !rolling) {
            String w = waitingFor();
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                    w.isEmpty() ? "" : "Waiting for " + w + " to keep it", x + 16 * s, y, Settings.GOLD_COLOR);
        }
        for (int i = 0; i < btnRects.size(); i++) {
            float[] r = btnRects.get(i);
            box(sb, r[0], r[1], r[2], r[3], hovered(r) ? BTN_HOVER : BTN);
            FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, btnText.get(i), r[0] + r[2] / 2f,
                    r[1] + r[3] / 2f, Settings.GOLD_COLOR);
        }
    }

    private static void box(SpriteBatch sb, float x, float y, float w, float h, Color c) {
        sb.setColor(c);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x, y, w, h);
        sb.setColor(Color.WHITE);
    }
}

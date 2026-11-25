package BoardGame.multiplayer.ui;

import BoardGame.multiplayer.MP;
import BoardGame.multiplayer.Session;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.rooms.MonsterRoomElite;
import com.megacrit.cardcrawl.rooms.TreasureRoom;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Optional rule "Choose Your Relic": when the party gains a relic at an Elite or a Treasure
 * room, one relic per player is revealed (by the host) and each player picks a different one.
 * Clicking your relic reward opens this picker.
 */
public class RelicPicker {
    private static int revealFloor = -1;
    private static final ArrayList<String> revealed = new ArrayList<>();
    private static final HashMap<String, Integer> taken = new HashMap<>(); // relic id -> player
    private static RewardItem pending;
    private static RewardItem awaitingPick;
    private static boolean granting = false;
    private static boolean wasDown = false;

    private static boolean ruleActive() {
        return MP.inRun && MP.playerCount() > 1 && MP.chooseRelic && AbstractDungeon.currMapNode != null
                && (AbstractDungeon.getCurrRoom() instanceof MonsterRoomElite
                || AbstractDungeon.getCurrRoom() instanceof TreasureRoom);
    }

    public static void reset() {
        revealFloor = -1;
        revealed.clear();
        taken.clear();
        pending = null;
        awaitingPick = null;
    }

    /** Host: reveal as soon as the party enters an Elite or Treasure room. */
    public static void onRoomEntered() {
        if (ruleActive()) hostReveal();
    }

    /** Host: reveal one relic per player for this room. */
    private static void hostReveal() {
        if (!MP.isHost() || revealFloor == AbstractDungeon.floorNum) return;
        JsonObject o = MP.msg("relicreveal");
        o.addProperty("floor", AbstractDungeon.floorNum);
        JsonArray ids = new JsonArray();
        for (int i = 0; i < MP.playerCount(); i++)
            ids.add(new com.google.gson.JsonPrimitive(BoardGame.relics.AbstractBGRelic.drawFromRelicDeck().relicId));
        o.add("ids", ids);
        MP.send(o);
        revealFloor = AbstractDungeon.floorNum; // the echo fills the list
    }

    public static void onMessage(String type, int from, JsonObject m) {
        if (type.equals("relicreveal")) {
            revealFloor = m.get("floor").getAsInt();
            revealed.clear();
            taken.clear();
            for (JsonElement e : m.getAsJsonArray("ids")) revealed.add(e.getAsString());
        } else if (type.equals("relicpick")) {
            if (m.get("floor").getAsInt() != revealFloor) return;
            String id = m.get("id").getAsString();
            Integer first = taken.putIfAbsent(id, from);
            if (from != MP.myId) {
                if (first == null) Session.toast(Session.nameOf(from) + " chose " + nameOf(id));
                return;
            }
            // our own pick came back through the stream: it's ours only if nobody was first
            RewardItem item = awaitingPick;
            awaitingPick = null;
            if (first != null && first != MP.myId) {
                Session.toast(Session.nameOf(first) + " took " + nameOf(id) + " first; choose another");
                pending = item;
                return;
            }
            if (item == null) return;
            item.relic = RelicLibrary.getRelic(id).makeCopy();
            granting = true;
            try {
                if (item.claimReward()) AbstractDungeon.combatRewardScreen.rewards.remove(item);
                AbstractDungeon.combatRewardScreen.positionRewards();
            } finally {
                granting = false;
            }
        }
    }

    private static String nameOf(String id) {
        AbstractRelic r = RelicLibrary.getRelic(id);
        return r == null ? id : r.name;
    }

    /** Clicking a relic reward at an Elite/Treasure room opens the picker instead. */
    @SpirePatch2(clz = RewardItem.class, method = "claimReward")
    public static class Intercept {
        @SpirePrefixPatch
        public static SpireReturn<Boolean> prefix(RewardItem __instance) {
            if (granting || __instance.type != RewardItem.RewardType.RELIC || !ruleActive()) return SpireReturn.Continue();
            if (__instance.relicLink != null) return SpireReturn.Continue(); // sapphire key pairing: leave as is
            hostReveal();
            pending = __instance;
            return SpireReturn.Return(false);
        }
    }

    // ---------------- picker UI ----------------

    public static boolean open() {
        return pending != null;
    }

    private static float[] slot(int i, int n) {
        float w = 200 * Settings.scale, gap = 30 * Settings.scale;
        float total = n * w + (n - 1) * gap;
        float x = Settings.WIDTH / 2f - total / 2f + i * (w + gap);
        float y = Settings.HEIGHT / 2f - 100 * Settings.scale;
        return new float[]{x, y, w, 220 * Settings.scale};
    }

    public static void update() {
        boolean down = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        boolean clicked = down && !wasDown;
        wasDown = down;
        if (pending == null) return;
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            pending = null;
            return;
        }
        if (!clicked || revealFloor != AbstractDungeon.floorNum) return;
        for (int i = 0; i < revealed.size(); i++) {
            float[] r = slot(i, revealed.size());
            if (InputHelper.mX < r[0] || InputHelper.mX > r[0] + r[2] || InputHelper.mY < r[1] || InputHelper.mY > r[1] + r[3])
                continue;
            String id = revealed.get(i);
            Integer who = taken.get(id);
            if (who != null && who != MP.myId) {
                Session.toast(Session.nameOf(who) + " already took that relic");
                return;
            }
            JsonObject o = MP.msg("relicpick");
            o.addProperty("floor", revealFloor);
            o.addProperty("id", id);
            MP.send(o);
            awaitingPick = pending; // granted when our pick comes back first in the shared order
            pending = null;
            return;
        }
    }

    public static void render(SpriteBatch sb) {
        if (pending == null) return;
        sb.setColor(new Color(0f, 0f, 0f, 0.75f));
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0, 0, Settings.WIDTH, Settings.HEIGHT);
        sb.setColor(Color.WHITE);
        FontHelper.renderFontCentered(sb, FontHelper.bannerNameFont, "Choose your relic", Settings.WIDTH / 2f,
                Settings.HEIGHT / 2f + 220 * Settings.scale, Settings.GOLD_COLOR);
        if (revealFloor != AbstractDungeon.floorNum || revealed.isEmpty()) {
            FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, "Waiting for the host to reveal the relics...",
                    Settings.WIDTH / 2f, Settings.HEIGHT / 2f, Settings.CREAM_COLOR);
            return;
        }
        for (int i = 0; i < revealed.size(); i++) {
            float[] r = slot(i, revealed.size());
            AbstractRelic relic = RelicLibrary.getRelic(revealed.get(i));
            Integer who = taken.get(revealed.get(i));
            boolean hov = InputHelper.mX >= r[0] && InputHelper.mX <= r[0] + r[2] && InputHelper.mY >= r[1]
                    && InputHelper.mY <= r[1] + r[3];
            sb.setColor(who != null ? new Color(0.2f, 0.2f, 0.2f, 0.9f) : hov ? new Color(0.3f, 0.4f, 0.5f, 0.95f)
                    : new Color(0.12f, 0.15f, 0.2f, 0.95f));
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, r[0], r[1], r[2], r[3]);
            sb.setColor(who != null ? Color.GRAY : Color.WHITE);
            if (relic != null && relic.img != null) {
                float s = 128 * Settings.scale;
                sb.draw(relic.img, r[0] + r[2] / 2f - s / 2f, r[1] + r[3] - s - 20 * Settings.scale, s, s);
            }
            sb.setColor(Color.WHITE);
            FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, relic == null ? revealed.get(i) : relic.name,
                    r[0] + r[2] / 2f, r[1] + 50 * Settings.scale, Settings.CREAM_COLOR);
            if (who != null) FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, "Taken by " + Session.nameOf(who),
                    r[0] + r[2] / 2f, r[1] + 22 * Settings.scale, Settings.RED_TEXT_COLOR);
            if (hov && relic != null)
                TipHelper.renderGenericTip(r[0] + r[2] + 10 * Settings.scale, r[1] + r[3], relic.name, relic.description);
        }
        FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, "Esc to decide later", Settings.WIDTH / 2f,
                Settings.HEIGHT / 2f - 150 * Settings.scale, Settings.CREAM_COLOR);
    }
}

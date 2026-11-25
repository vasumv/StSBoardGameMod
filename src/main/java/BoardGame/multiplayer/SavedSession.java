package BoardGame.multiplayer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ModHelper;

/**
 * Save and continue for multiplayer runs. Every game already autosaves its own
 * character at each room. This file remembers the party (seed, ascension, players,
 * rows, your id) so the host can offer "Continue saved run" in the lobby; everyone
 * then loads their own save and the party carries on from the same room.
 */
public class SavedSession {
    private static FileHandle file() {
        return Gdx.files.local("preferences/bgmp_session.json");
    }

    /** Called when a new room starts. */
    public static void write() {
        if (!MP.inRun || AbstractDungeon.player == null) return;
        try {
            JsonObject o = new JsonObject();
            o.addProperty("seed", MP.seed);
            o.addProperty("asc", MP.ascension);
            o.addProperty("me", MP.myId);
            o.addProperty("floor", AbstractDungeon.floorNum);
            o.addProperty("act", AbstractDungeon.actNum);
            o.add("roster", roster());
            file().writeString(o.toString(), false);
        } catch (Throwable t) {
            MP.logger.error("could not save the multiplayer session", t);
        }
    }

    public static JsonArray roster() {
        JsonArray roster = new JsonArray();
        for (MP.Player p : MP.players) {
            JsonObject pj = new JsonObject();
            pj.addProperty("id", p.id);
            pj.addProperty("name", p.name);
            pj.addProperty("char", p.character);
            pj.addProperty("row", p.row);
            roster.add(pj);
        }
        return roster;
    }

    public static JsonObject read() {
        try {
            FileHandle f = file();
            if (!f.exists()) return null;
            JsonObject o = new JsonParser().parse(f.readString()).getAsJsonObject();
            return CardCrawlGame.characterManager.anySaveFileExists() ? o : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void clear() {
        try {
            file().delete();
        } catch (Throwable ignored) {
        }
    }

    // Continue is two-phase: the host asks, everyone checks their own save, and only when
    // every player has a matching save (same run, same act and floor) does anyone load.
    private static final java.util.HashSet<Integer> okFrom = new java.util.HashSet<>();
    private static JsonObject asking;

    /** Host: offer the saved run to everyone in the lobby. */
    public static void hostContinue() {
        JsonObject s = read();
        if (s == null || !MP.isHost()) return;
        okFrom.clear();
        asking = s;
        JsonObject o = MP.msg("continueask");
        o.add("save", s);
        MP.send(o);
    }

    private static boolean matches(JsonObject mine, JsonObject theirs) {
        return mine != null && mine.get("seed").getAsLong() == theirs.get("seed").getAsLong()
                && mine.get("floor").getAsInt() == theirs.get("floor").getAsInt()
                && mine.get("act").getAsInt() == theirs.get("act").getAsInt();
    }

    public static void onMessage(String t, int from, JsonObject m) {
        switch (t) {
            case "continueask": {
                boolean ok = matches(read(), m.getAsJsonObject("save"));
                JsonObject r = MP.msg(ok ? "continueok" : "continueno");
                MP.send(r);
                return;
            }
            case "continueok":
                if (!MP.isHost() || asking == null) return;
                okFrom.add(from);
                for (MP.Player p : MP.players) if (p.connected && !okFrom.contains(p.id)) return;
                JsonObject go = MP.msg("continuego");
                go.add("save", asking);
                asking = null;
                MP.send(go);
                return;
            case "continueno":
                if (MP.isHost()) asking = null;
                Session.toast(Session.nameOf(from) + " doesn't have a save of this run at the same room;"
                        + " can't continue it together");
                return;
            case "continuego":
                onContinue(m);
                return;
        }
    }

    /** Everyone: load your own save of the same run. */
    public static void onContinue(JsonObject m) {
        JsonObject hostSave = m.getAsJsonObject("save");
        JsonObject mine = read();
        if (!matches(mine, hostSave)) {
            Session.toast("You don't have a save of this run; can't continue it");
            MP.leave();
            return;
        }
        int savedId = mine.get("me").getAsInt();
        if (!MP.isHost()) MP.claimId(savedId);
        MP.seed = hostSave.get("seed").getAsLong();
        MP.ascension = hostSave.get("asc").getAsInt();
        MP.players.clear();
        for (JsonElement e : hostSave.getAsJsonArray("roster")) {
            JsonObject pj = e.getAsJsonObject();
            MP.Player p = new MP.Player();
            p.id = pj.get("id").getAsInt();
            p.name = pj.get("name").getAsString();
            p.character = pj.get("char").getAsInt();
            p.row = pj.get("row").getAsInt();
            MP.players.add(p);
        }
        MP.inRun = true;
        Session.soloBonusRemoved = true; // already applied in the saved run
        BoardGame.multiplayer.ui.LobbyScreen.open = false;
        // what the main menu's Continue button does
        CardCrawlGame.loadingSave = true;
        CardCrawlGame.chosenCharacter = CardCrawlGame.characterManager.loadChosenCharacter().chosenClass;
        CardCrawlGame.mainMenuScreen.isFadingOut = true;
        CardCrawlGame.mainMenuScreen.fadeOutMusic();
        Settings.isDailyRun = false;
        Settings.isTrial = false;
        ModHelper.setModsFalse();
    }
}

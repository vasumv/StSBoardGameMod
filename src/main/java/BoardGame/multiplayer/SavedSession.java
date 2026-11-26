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
            o.addProperty("lastStand", MP.lastStand);
            o.addProperty("chooseRelic", MP.chooseRelic);
            o.addProperty("sequential", MP.sequential);
            o.addProperty("finalAct", MP.finalAct);
            o.addProperty("lastStandUsed", Session.lastStandUsed);
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
            pj.addProperty("dead", p.dead);
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
    private static final java.util.HashMap<Integer, String> okNames = new java.util.HashMap<>();
    private static JsonObject asking;

    /** Host: offer the saved run to everyone in the lobby. */
    public static void hostContinue() {
        JsonObject s = read();
        if (s == null || !MP.isHost()) return;
        okFrom.clear();
        okNames.clear();
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
                PartySaves.received = null;
                boolean ok = matches(read(), m.getAsJsonObject("save"));
                JsonObject r = MP.msg("continueok");
                r.addProperty("has", ok);
                r.addProperty("name", MP.myName);
                MP.send(r);
                return;
            }
            case "continueno": // older versions
            case "continueok": {
                if (!MP.isHost() || asking == null) return;
                MP.Player pl = MP.player(from);
                String name = m.has("name") ? m.get("name").getAsString() : pl == null ? "" : pl.name;
                boolean has = m.has("has") ? m.get("has").getAsBoolean() : t.equals("continueok");
                if (!has && PartySaves.savedFor(name, asking) == null) {
                    asking = null;
                    Session.toast(name + " has no save of this run at this room, and the host has none"
                            + " for them either: can't continue");
                    return;
                }
                okFrom.add(from);
                okNames.put(from, name);
                for (MP.Player p : MP.players) if (p.connected && !okFrom.contains(p.id)) return;
                // hand everyone their save from the host's copies (the latest one each game sent)
                for (java.util.Map.Entry<Integer, String> e : okNames.entrySet()) {
                    JsonObject s = PartySaves.savedFor(e.getValue(), asking);
                    if (s == null || e.getKey() == MP.myId) continue;
                    JsonObject y = new JsonParser().parse(s.toString()).getAsJsonObject();
                    y.addProperty("t", "yoursave");
                    BoardGame.multiplayer.net.Net.sendDirect(e.getKey(), y);
                }
                JsonObject go = MP.msg("continuego");
                go.add("save", asking);
                asking = null;
                MP.send(go);
                return;
            }
            case "continuego":
                onContinue(m);
                return;
        }
    }

    /** Everyone: load your own save of the same run. */
    public static void onContinue(JsonObject m) {
        JsonObject hostSave = m.getAsJsonObject("save");
        JsonObject mine = read();
        JsonObject fromHost = PartySaves.received;
        PartySaves.received = null;
        if (fromHost == null && MP.isHost() && !matches(mine, hostSave)) { // host lost its own save
            JsonObject s = PartySaves.savedFor(MP.myName, hostSave);
            if (s != null) {
                PartySaves.receive(s);
                fromHost = PartySaves.received;
                PartySaves.received = null;
            }
        }
        if (fromHost == null && !matches(mine, hostSave)) {
            Session.toast("You don't have a save of this run; can't continue it");
            MP.leave();
            return;
        }
        RunStart.resetModStatics();
        // my id in the saved party: by name (join order may differ this time), else my own record
        int savedId = mine != null && matches(mine, hostSave) ? mine.get("me").getAsInt() : MP.myId;
        for (JsonElement e : hostSave.getAsJsonArray("roster")) {
            JsonObject pj = e.getAsJsonObject();
            if (pj.get("name").getAsString().equals(MP.myName)) savedId = pj.get("id").getAsInt();
        }
        if (MP.isHost()) savedId = 0;
        if (!MP.isHost()) MP.claimId(savedId);
        MP.seed = hostSave.get("seed").getAsLong();
        MP.ascension = hostSave.get("asc").getAsInt();
        MP.lastStand = hostSave.has("lastStand") && hostSave.get("lastStand").getAsBoolean();
        MP.chooseRelic = hostSave.has("chooseRelic") && hostSave.get("chooseRelic").getAsBoolean();
        MP.sequential = hostSave.has("sequential") && hostSave.get("sequential").getAsBoolean();
        MP.finalAct = hostSave.has("finalAct") && hostSave.get("finalAct").getAsBoolean();
        MP.players.clear();
        for (JsonElement e : hostSave.getAsJsonArray("roster")) {
            JsonObject pj = e.getAsJsonObject();
            MP.Player p = new MP.Player();
            p.id = pj.get("id").getAsInt();
            p.name = pj.get("name").getAsString();
            p.character = pj.get("char").getAsInt();
            p.row = pj.get("row").getAsInt();
            p.dead = pj.has("dead") && pj.get("dead").getAsBoolean();
            MP.players.add(p);
        }
        MP.inRun = true;
        Session.soloBonusRemoved = true; // already applied in the saved run
        Session.lastStandUsed = hostSave.has("lastStandUsed") && hostSave.get("lastStandUsed").getAsBoolean();
        BoardGame.multiplayer.ui.LobbyScreen.open = false;
        // what the main menu's Continue button does
        CardCrawlGame.loadingSave = true;
        // load the save of the character this player had in the run (other saves may exist too)
        MP.Player me = MP.me();
        int ch = me == null ? 0 : me.character;
        com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass[] classes = {
                BoardGame.characters.BGIronclad.Enums.BG_IRONCLAD, BoardGame.characters.BGSilent.Enums.BG_SILENT,
                BoardGame.characters.BGDefect.Enums.BG_DEFECT, BoardGame.characters.BGWatcher.Enums.BG_WATCHER};
        CardCrawlGame.chosenCharacter = classes[Math.max(0, Math.min(3, ch))];
        if (fromHost != null)
            CardCrawlGame.chosenCharacter = com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass
                    .valueOf(fromHost.get("cls").getAsString());
        CardCrawlGame.mainMenuScreen.isFadingOut = true;
        CardCrawlGame.mainMenuScreen.fadeOutMusic();
        Settings.isDailyRun = false;
        Settings.isTrial = false;
        ModHelper.setModsFalse();
    }
}

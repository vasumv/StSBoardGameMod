package BoardGame.multiplayer;

import BoardGame.multiplayer.net.Net;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.AsyncSaver;
import com.megacrit.cardcrawl.saveAndContinue.SaveAndContinue;

/**
 * The host keeps every player's latest save. Each time a game writes its run save (at the
 * start of every room), a copy goes to the host only. On Continue, the host hands each
 * player their own save back, so nobody needs a matching local save.
 */
public class PartySaves {
    private static FileHandle dir() {
        return Gdx.files.local("preferences/bgmp_party/");
    }

    private static FileHandle fileFor(String name) {
        return dir().child(name.replaceAll("[^A-Za-z0-9_-]", "_") + ".json");
    }

    @SpirePatch2(clz = AsyncSaver.class, method = "save")
    public static class Capture {
        @SpirePrefixPatch
        public static void prefix(String filepath, String data) {
            try {
                if (!MP.inRun || MP.playerCount() < 2 || filepath == null || !filepath.endsWith(".autosave")
                        || AbstractDungeon.player == null) return;
                if (!filepath.equals(SaveAndContinue.getPlayerSavePath(AbstractDungeon.player.chosenClass))) return;
                JsonObject o = new JsonObject(); // no room/turn tags: never held back
                o.addProperty("t", "partysave");
                o.addProperty("name", MP.myName);
                o.addProperty("cls", AbstractDungeon.player.chosenClass.name());
                o.addProperty("seed", MP.seed);
                o.addProperty("floor", AbstractDungeon.floorNum);
                o.addProperty("act", AbstractDungeon.actNum);
                o.addProperty("data", data);
                if (com.megacrit.cardcrawl.core.CardCrawlGame.dungeon instanceof BoardGame.dungeons.AbstractBGDungeon)
                    o.add("decks", BoardGame.multiplayer.combat.SharedDecks.save());
                if (MP.isHost()) store(o);
                else Net.send(o); // the host keeps it; it is not shared with the others
            } catch (Throwable t) {
                MP.logger.error("could not send the party save", t);
            }
        }
    }

    /** Host: keep a player's latest save. */
    public static void store(JsonObject o) {
        if (!MP.isHost()) return;
        try {
            o.remove("n");
            o.remove("fl");
            o.remove("tn");
            fileFor(o.get("name").getAsString()).writeString(o.toString(), false);
            MP.logger.info("party save stored for " + o.get("name").getAsString() + " (floor "
                    + o.get("floor").getAsInt() + ")");
        } catch (Throwable t) {
            MP.logger.error("could not store a party save", t);
        }
    }

    /** Host: the stored save of this player for this run and room, or null. */
    public static JsonObject savedFor(String name, JsonObject session) {
        try {
            FileHandle f = fileFor(name);
            if (!f.exists()) return null;
            JsonObject o = new JsonParser().parse(f.readString()).getAsJsonObject();
            if (o.get("seed").getAsLong() != session.get("seed").getAsLong()
                    || o.get("floor").getAsInt() != session.get("floor").getAsInt()
                    || o.get("act").getAsInt() != session.get("act").getAsInt()) return null;
            return o;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Anyone: write a save the host sent back, so the normal loader finds it. */
    public static JsonObject received = null;

    public static void receive(JsonObject o) {
        try {
            AbstractPlayer.PlayerClass cls = AbstractPlayer.PlayerClass.valueOf(o.get("cls").getAsString());
            Gdx.files.local(SaveAndContinue.getPlayerSavePath(cls)).writeString(o.get("data").getAsString(), false);
            received = o;
            MP.logger.info("loaded my save from the host (floor " + o.get("floor").getAsInt() + ")");
        } catch (Throwable t) {
            MP.logger.error("could not write the save from the host", t);
        }
    }
}

package BoardGame.multiplayer;

import BoardGame.multiplayer.net.Net;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

/**
 * Multiplayer session state and message dispatch. Everything here runs on the game
 * thread; {@link #update()} drains the ordered message stream each frame.
 */
public class MP {
    public static final Logger logger = LogManager.getLogger(MP.class.getName());
    public static final String[] CHARACTER_NAMES = {"Ironclad", "Silent", "Defect", "Watcher"};

    public static class Player {
        public int id;
        public String name = "?";
        public int character = 0;
        public int row = 0;
        // live status shown to teammates
        public int hp, maxHp, block, energy, handSize;
        public String powers = "";
        public boolean endTurnReady;
        public boolean connected = true;
        public int gold;
        public String where = "";
    }

    /** True once a session exists (hosting or joined), in the lobby or in a run. */
    public static boolean active = false;
    /** True from run start until the session ends. */
    public static boolean inRun = false;
    public static int myId = -1;
    public static long seed;
    public static final ArrayList<Player> players = new ArrayList<>();
    public static String myName = System.getProperty("bg.mp.name", "Player");
    public static int myCharacter = Integer.getInteger("bg.mp.char", 0);
    public static String lastError = "";

    public static Player me() {
        return player(myId);
    }

    public static Player player(int id) {
        for (Player p : players) if (p.id == id) return p;
        return null;
    }

    public static Player playerInRow(int row) {
        for (Player p : players) if (p.row == row) return p;
        return null;
    }

    public static int myRow() {
        Player p = me();
        return p == null ? 0 : p.row;
    }

    public static int playerCount() {
        return players.size();
    }

    public static boolean isHost() {
        return active && Net.isHost();
    }

    // ---- session control ----

    public static boolean host(int port) {
        try {
            Net.host(port);
        } catch (Exception e) {
            lastError = "Could not host: " + e.getMessage();
            logger.error(lastError);
            return false;
        }
        reset();
        active = true;
        myId = 0;
        sendHello();
        return true;
    }

    public static boolean join(String address, int port) {
        try {
            Net.join(address, port);
        } catch (Exception e) {
            lastError = "Could not join: " + e.getMessage();
            logger.error(lastError);
            return false;
        }
        reset();
        active = true;
        return true; // hello is sent when the welcome (with our id) arrives
    }

    public static void leave() {
        Net.shutdown();
        reset();
    }

    private static void reset() {
        active = false;
        inRun = false;
        myId = -1;
        players.clear();
        lastError = "";
        Session.reset();
    }

    private static void sendHello() {
        JsonObject o = msg("hello");
        o.addProperty("name", myName);
        o.addProperty("char", myCharacter);
        Net.send(o);
    }

    public static void setCharacter(int c) {
        myCharacter = c;
        if (!active || inRun) return;
        JsonObject o = msg("char");
        o.addProperty("char", c);
        Net.send(o);
    }

    /** Host only: start the run for everyone in the lobby. */
    public static void startRun(long seed) {
        if (!isHost() || inRun) return;
        JsonObject o = msg("start");
        o.addProperty("seed", seed);
        JsonArray roster = new JsonArray();
        int row = 0;
        for (Player p : players) {
            JsonObject pj = new JsonObject();
            pj.addProperty("id", p.id);
            pj.addProperty("name", p.name);
            pj.addProperty("char", p.character);
            pj.addProperty("row", row++);
            roster.add(pj);
        }
        o.add("roster", roster);
        Net.send(o);
    }

    public static JsonObject msg(String type) {
        JsonObject o = new JsonObject();
        o.addProperty("t", type);
        return o;
    }

    public static void send(JsonObject o) {
        if (active) Net.send(o);
    }

    // ---- stream processing ----

    public static void update() {
        autoStartFromProperties();
        if (!active) return;
        JsonObject m;
        while ((m = Net.inbox.peek()) != null) {
            try {
                if (!Session.canProcess(m)) break;
                Net.inbox.poll();
                handle(m);
            } catch (Throwable t) {
                Net.inbox.poll();
                logger.error("failed handling " + m, t);
            }
        }
        Session.update();
    }

    private static void handle(JsonObject m) {
        String t = m.get("t").getAsString();
        int from = m.has("from") ? m.get("from").getAsInt() : -1;
        switch (t) {
            case "welcome":
                myId = m.get("id").getAsInt();
                sendHello();
                return;
            case "hello": {
                if (inRun) return; // no joining mid-run yet
                Player p = player(from);
                if (p == null) {
                    p = new Player();
                    p.id = from;
                    players.add(p);
                }
                p.name = m.get("name").getAsString();
                p.character = m.get("char").getAsInt();
                if (isHost()) sendRoster();
                return;
            }
            case "roster": {
                if (inRun) return;
                readRoster(m.getAsJsonArray("roster"));
                return;
            }
            case "char": {
                Player p = player(from);
                if (p != null && !inRun) p.character = m.get("char").getAsInt();
                return;
            }
            case "left": {
                Player p = player(m.get("who").getAsInt());
                if (p != null) {
                    p.connected = false;
                    if (!inRun) players.remove(p);
                    Session.toast(p.name + " disconnected");
                }
                return;
            }
            case "start":
                readRoster(m.getAsJsonArray("roster"));
                seed = m.get("seed").getAsLong();
                inRun = true;
                logger.info("starting multiplayer run, seed " + seed + ", " + players.size() + " players, I am "
                        + myId + " row " + myRow());
                RunStart.begin();
                return;
            default:
                Session.handle(t, from, m);
        }
    }

    private static void sendRoster() {
        JsonObject o = msg("roster");
        JsonArray roster = new JsonArray();
        for (Player p : players) {
            JsonObject pj = new JsonObject();
            pj.addProperty("id", p.id);
            pj.addProperty("name", p.name);
            pj.addProperty("char", p.character);
            pj.addProperty("row", 0);
            roster.add(pj);
        }
        o.add("roster", roster);
        Net.send(o);
    }

    private static void readRoster(JsonArray roster) {
        players.clear();
        for (JsonElement e : roster) {
            JsonObject pj = e.getAsJsonObject();
            Player p = new Player();
            p.id = pj.get("id").getAsInt();
            p.name = pj.get("name").getAsString();
            p.character = pj.get("char").getAsInt();
            p.row = pj.get("row").getAsInt();
            players.add(p);
        }
    }

    // ---- test hook: -Dbg.mp.host=PORT or -Dbg.mp.join=HOST:PORT connects once the main menu is up ----

    private static boolean autoStartDone = false;

    private static void autoStartFromProperties() {
        if (autoStartDone) return;
        if (com.megacrit.cardcrawl.core.CardCrawlGame.mainMenuScreen == null) return;
        autoStartDone = true;
        String h = System.getProperty("bg.mp.host"), j = System.getProperty("bg.mp.join");
        if (h != null) host(Integer.parseInt(h));
        else if (j != null) join(j.substring(0, j.lastIndexOf(':')), Integer.parseInt(j.substring(j.lastIndexOf(':') + 1)));
    }

    public static String status() {
        StringBuilder sb = new StringBuilder("active=" + active + " inRun=" + inRun + " host=" + isHost() + " me="
                + myId + " seed=" + seed + " players=");
        for (Player p : players)
            sb.append('[').append(p.id).append(' ').append(p.name).append(' ').append(CHARACTER_NAMES[p.character])
                    .append(" row").append(p.row).append(p.endTurnReady ? " ready" : "").append("] ");
        return sb.toString() + Session.status();
    }
}

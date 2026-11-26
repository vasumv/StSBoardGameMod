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
        public boolean dead = false;
        public int gold;
        public String where = "";
        /** Their deck (card id#upgrades|...), for the deck viewer. */
        public String deck = null;
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
    /** Optional rule "Last Stand": in a Boss fight, the others fight on when a player dies. */
    public static boolean lastStand = false;
    /** Optional rule "Sequential Turns": players act one at a time, bottom row first. */
    public static boolean sequential = false;

    public static void setSequential(boolean on) {
        if (!isHost() || inRun) return;
        sequential = on;
        sendCfg();
    }

    /** Optional rule "Choose Your Relic": Elite/Treasure relics are revealed one per player and picked. */
    public static boolean chooseRelic = false;

    public static void setChooseRelic(boolean on) {
        if (!isHost() || inRun) return;
        chooseRelic = on;
        sendCfg();
    }

    private static void sendCfg() {
        JsonObject o = msg("cfg");
        o.addProperty("asc", ascension);
        o.addProperty("lastStand", lastStand);
        o.addProperty("chooseRelic", chooseRelic);
        o.addProperty("sequential", sequential);
        Net.send(o);
    }

    public static void setLastStand(boolean on) {
        if (!isHost() || inRun) return;
        lastStand = on;
        sendCfg();
    }

    /** Ascension level for the run; chosen by the host in the lobby. */
    public static int ascension = 0;

    public static void setAscension(int level) {
        if (!isHost() || inRun) return;
        level = Math.max(0, Math.min(BoardGame.patches.Ascension.CURRENT_MAX_ASCENSION, level));
        ascension = level;
        sendCfg();
    }

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
        joinedAddress = address;
        joinedPort = port;
        viaSteam = false;
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

    // ---------------- reconnecting ----------------

    /** Last message number seen from the shared stream. */
    public static long lastSeq = -1;
    private static String joinedAddress;
    private static int joinedPort;
    public static volatile boolean reconnecting = false;
    /** Act 4 (keys, burning elites) on for the party: the host's setting, so every map matches. */
    public static boolean finalAct = false;
    private static boolean viaSteam = false;

    private static void startReconnect() {
        if (reconnecting || isHost()) return;
        reconnecting = true;
        Session.toast("Lost the connection to the host. Reconnecting...");
        if (viaSteam) {
            BoardGame.multiplayer.net.SteamNet.rejoin();
            return;
        }
        Thread t = new Thread(() -> {
            for (int i = 0; i < 100 && reconnecting; i++) {
                try {
                    Net.rejoin(joinedAddress, joinedPort);
                    return; // the welcome triggers the resume request
                } catch (Exception e) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            com.badlogic.gdx.Gdx.app.postRunnable(() -> {
                reconnecting = false;
                Session.toast("Could not reconnect to the host");
            });
        }, "bg-mp-reconnect");
        t.setDaemon(true);
        t.start();
    }

    /** Take back our id from a saved run (the host remaps our connection to it). */
    public static void claimId(int id) {
        myId = id;
        JsonObject o = msg("resume");
        o.addProperty("id", id);
        o.addProperty("n", lastSeq);
        o.addProperty("claim", true);
        Net.send(o);
    }

    private static void sendResume() {
        JsonObject o = msg("resume");
        o.addProperty("id", myId);
        o.addProperty("n", lastSeq);
        Net.send(o);
    }

    /** True while a join attempt is in progress (connecting happens off the game thread). */
    public static volatile boolean connecting = false;

    public static void joinAsync(String address, int port) {
        if (connecting) return;
        connecting = true;
        lastError = "";
        Thread t = new Thread(() -> {
            Exception err = null;
            try {
                java.net.Socket probe = new java.net.Socket();
                probe.connect(new java.net.InetSocketAddress(address, port), 6000);
                probe.close();
            } catch (Exception e) {
                err = e;
            }
            final Exception fe = err;
            com.badlogic.gdx.Gdx.app.postRunnable(() -> {
                connecting = false;
                if (fe != null) lastError = "Could not reach " + address + ":" + port + " (" + fe.getMessage() + ")";
                else if (!join(address, port)) Session.toast(lastError);
            });
        }, "bg-mp-connect");
        t.setDaemon(true);
        t.start();
    }

    /** Steam lobby created: we are the host. */
    public static void onSteamHosting() {
        reset();
        active = true;
        myId = 0;
        String n = BoardGame.multiplayer.net.SteamNet.myName();
        if (n != null) myName = n;
        sendHello();
    }

    /** Joined a Steam lobby: knock so the host opens the connection and welcomes us. */
    public static void onSteamJoined() {
        viaSteam = true;
        if (reconnecting && inRun) {
            Net.send(msg("knock")); // welcome -> resume
            return;
        }
        reset();
        active = true;
        String n = BoardGame.multiplayer.net.SteamNet.myName();
        if (n != null) myName = n;
        Net.send(msg("knock"));
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
        ascension = 0;
        lastStand = false;
        chooseRelic = false;
        sequential = false;
        lastSeq = -1;
        reconnecting = false;
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
        o.addProperty("asc", ascension);
        o.addProperty("lastStand", lastStand);
        o.addProperty("chooseRelic", chooseRelic);
        o.addProperty("sequential", sequential);
        boolean wasInRun = inRun;
        inRun = false;
        com.megacrit.cardcrawl.core.Settings.setFinalActAvailability(); // the host's own unlock
        inRun = wasInRun;
        finalAct = com.megacrit.cardcrawl.core.Settings.isFinalActAvailable;
        o.addProperty("finalAct", finalAct);
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
        if (inRun) {
            o.addProperty("fl", roomKey());
            if (BoardGame.multiplayer.combat.MonsterSync.active())
                o.addProperty("tn", com.megacrit.cardcrawl.actions.GameActionManager.turn);
        }
        return o;
    }

    /** Which room this game is in (act and floor), used to keep messages in step with rooms. */
    public static int roomKey() {
        try {
            if (!com.megacrit.cardcrawl.core.CardCrawlGame.isInARun() || com.megacrit.cardcrawl.dungeons.AbstractDungeon.currMapNode == null)
                return -1;
            return com.megacrit.cardcrawl.dungeons.AbstractDungeon.actNum * 1000 + com.megacrit.cardcrawl.dungeons.AbstractDungeon.floorNum;
        } catch (Throwable t) {
            return -1;
        }
    }

    public static void send(JsonObject o) {
        if (active) Net.send(o);
    }

    // ---- stream processing ----

    private static String loggedError = "";

    public static void update() {
        if (lastError != null && !lastError.isEmpty() && !lastError.equals(loggedError))
            logger.error("[shown to player] " + lastError);
        loggedError = lastError == null ? "" : lastError;
        autoStartFromProperties();
        Net.poll();
        if (!active) return;
        // back at the main menu after death/victory: this game's run is over; go back to the lobby
        if (inRun && !com.megacrit.cardcrawl.core.CardCrawlGame.isInARun()
                && com.megacrit.cardcrawl.core.CardCrawlGame.mainMenuScreen != null
                && !com.megacrit.cardcrawl.core.CardCrawlGame.mainMenuScreen.isFadingOut
                && com.megacrit.cardcrawl.core.CardCrawlGame.mode == com.megacrit.cardcrawl.core.CardCrawlGame.GameMode.CHAR_SELECT
                && !RunStart.starting()) {
            inRun = false;
            reconnecting = false;
            Net.clearResuming();
            for (Player p : players) {
                p.dead = false;
                p.endTurnReady = false;
            }
            Session.reset();
        }
        JsonObject m;
        while ((m = Net.inbox.peek()) != null) {
            if (!Session.canProcess(m)) break;
            Net.inbox.poll();
            try {
                handle(m);
            } catch (Throwable t) {
                logger.error("failed handling " + m, t);
            }
        }
        Session.update();
    }

    private static void handle(JsonObject m) {
        String t = m.get("t").getAsString();
        int from = m.has("from") ? m.get("from").getAsInt() : -1;
        if (m.has("n")) {
            long n = m.get("n").getAsLong();
            if (n <= lastSeq) return; // already seen (replay overlap)
            lastSeq = n;
        }
        if (!t.equals("status") && !t.equals("partysave") && !t.equals("yoursave")) {
            String txt = m.toString();
            logger.info("[mp] " + (txt.length() > 400 ? txt.substring(0, 400) + "..." : txt));
        }
        switch (t) {
            case "knock":
                return;
            case "welcome":
                if (reconnecting && inRun) {
                    reconnecting = false;
                    sendResume(); // keep our old id; the host replays what we missed
                    Net.flushOutbox();
                    Session.toast("Reconnected");
                    return;
                }
                reconnecting = false;
                Net.clearResuming(); // a fresh join: don't hold our messages for a resume
                myId = m.get("id").getAsInt();
                sendHello();
                return;
            case "back": {
                Player p = player(from);
                if (p != null) {
                    p.connected = true;
                    if (from != myId) Session.toast(p.name + " reconnected");
                }
                return;
            }
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
                if (isHost()) {
                    sendRoster();
                    sendCfg();
                }
                return;
            }
            case "roster": {
                if (inRun) return;
                readRoster(m.getAsJsonArray("roster"));
                return;
            }
            case "partysave":
                PartySaves.store(m);
                return;
            case "yoursave":
                PartySaves.receive(m);
                return;
            case "continueask":
            case "continueok":
            case "continueno":
            case "continuego":
                SavedSession.onMessage(t, from, m);
                return;
            case "cfg":
                ascension = m.get("asc").getAsInt();
                if (m.has("lastStand")) lastStand = m.get("lastStand").getAsBoolean();
                if (m.has("chooseRelic")) chooseRelic = m.get("chooseRelic").getAsBoolean();
                if (m.has("sequential")) sequential = m.get("sequential").getAsBoolean();
                return;
            case "char": {
                Player p = player(from);
                if (p != null && !inRun) p.character = m.get("char").getAsInt();
                return;
            }
            case "left": {
                if (m.get("who").getAsInt() == 0 && !isHost() && inRun) {
                    startReconnect();
                    return;
                }
                Player p = player(m.get("who").getAsInt());
                if (p != null) {
                    p.connected = false;
                    if (!inRun) players.remove(p);
                    Session.toast(p.name + " disconnected");
                    if (inRun) Session.onPlayerLeft(p.id);
                }
                return;
            }
            case "start":
                readRoster(m.getAsJsonArray("roster"));
                seed = m.get("seed").getAsLong();
                ascension = m.has("asc") ? m.get("asc").getAsInt() : 0;
                lastStand = m.has("lastStand") && m.get("lastStand").getAsBoolean();
                chooseRelic = m.has("chooseRelic") && m.get("chooseRelic").getAsBoolean();
                sequential = m.has("sequential") && m.get("sequential").getAsBoolean();
                finalAct = m.has("finalAct") && m.get("finalAct").getAsBoolean();
                if (com.megacrit.cardcrawl.core.CardCrawlGame.mode != com.megacrit.cardcrawl.core.CardCrawlGame.GameMode.CHAR_SELECT
                        || com.megacrit.cardcrawl.core.CardCrawlGame.isInARun()) {
                    lastError = "The host started the run while you were not at the main menu";
                    leave();
                    return;
                }
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
        BoardGame.multiplayer.net.SteamNet.checkLaunchArgs();
        if (Boolean.getBoolean("bg.fast")) com.megacrit.cardcrawl.core.Settings.FAST_MODE = true;
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

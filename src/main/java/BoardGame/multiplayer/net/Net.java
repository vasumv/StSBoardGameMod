package BoardGame.multiplayer.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Transport: one host, any number of clients, JSON messages. Two kinds of links:
 * direct TCP (an address and port) and Steam (lobby + peer-to-peer, see SteamNet).
 *
 * The host is a pure sequencer. Every message from anyone (the host's own included)
 * is stamped with the sender id ("from") and a global sequence number ("n") and sent
 * to everybody, the sender included. All game logic runs on that single ordered
 * stream, identically on every machine. Messages land in {@link #inbox} and are
 * consumed on the game thread.
 */
public class Net {
    static final Logger logger = LogManager.getLogger(Net.class.getName());
    public static final ConcurrentLinkedQueue<JsonObject> inbox = new ConcurrentLinkedQueue<>();

    private static boolean host;
    private static ServerSocket server;
    static final CopyOnWriteArrayList<Peer> peers = new CopyOnWriteArrayList<>();
    static volatile Peer toHost;
    /** Client: messages sent while disconnected, delivered after reconnecting. */
    private static final java.util.ArrayList<String> outbox = new java.util.ArrayList<>();
    private static final AtomicLong seq = new AtomicLong();
    private static int nextPeerId = 1;
    private static final Object stampLock = new Object();
    private static boolean steamHosting = false;
    /** Host: every stamped message, so a reconnecting player can catch up. */
    private static final java.util.ArrayList<String> history = new java.util.ArrayList<>();
    private static final java.util.ArrayList<Long> historyN = new java.util.ArrayList<>();

    /** A connection to another player. */
    abstract static class Peer {
        int id;
        /** Replaced by a reconnect: its disconnect is not announced. */
        volatile boolean replaced = false;

        abstract void write(String line);

        abstract void close();
    }

    static class TcpPeer extends Peer {
        final Socket socket;
        final OutputStream out;
        private final java.util.concurrent.LinkedBlockingQueue<String> sendQueue = new java.util.concurrent.LinkedBlockingQueue<>();

        TcpPeer(Socket s, int id) throws IOException {
            this.socket = s;
            this.out = s.getOutputStream();
            this.id = id;
            s.setTcpNoDelay(true);
            s.setSoTimeout(15000); // the other side pings every few seconds; silence means the link is dead
            Thread w = new Thread(() -> {
                try {
                    while (!socket.isClosed()) {
                        String line = sendQueue.take();
                        try {
                            out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                            out.flush();
                        } catch (IOException e) {
                            unsent(line);
                            for (String rest : sendQueue) unsent(rest);
                            sendQueue.clear();
                            close();
                            return;
                        }
                    }
                } catch (InterruptedException ignored) {
                }
            }, "bg-mp-write");
            w.setDaemon(true);
            w.start();
        }

        @Override
        void write(String line) {
            if (socket.isClosed()) {
                unsent(line);
                return;
            }
            sendQueue.add(line);
        }

        @Override
        void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static boolean isHost() {
        return host;
    }

    public static boolean usingSteam() {
        return steamHosting || toHost instanceof SteamNet.SteamPeer;
    }

    public static void host(int port) throws IOException {
        shutdown();
        host = true;
        server = new ServerSocket(port);
        Thread t = new Thread(() -> {
            while (server != null && !server.isClosed()) {
                try {
                    Socket s = server.accept();
                    TcpPeer p = new TcpPeer(s, newPeerId());
                    welcome(p);
                    startReader(p);
                } catch (IOException e) {
                    break;
                }
            }
        }, "bg-mp-accept");
        t.setDaemon(true);
        t.start();
        logger.info("hosting on port " + port);
    }

    /** Host over Steam: the lobby is created by SteamNet; peers arrive as Steam users. */
    public static void hostSteam() {
        shutdown();
        host = true;
        steamHosting = true;
    }

    static int newPeerId() {
        synchronized (stampLock) {
            return nextPeerId++;
        }
    }

    /** Host: a new player connected. */
    /** Host: a new connection. It joins the broadcast only once it says hello (or resume). */
    static void welcome(Peer p) {
        JsonObject welcome = new JsonObject();
        welcome.addProperty("t", "welcome");
        welcome.addProperty("id", p.id);
        p.write(welcome.toString());
    }

    /** Client: between reconnecting and resuming, all sends wait in the outbox. */
    static volatile boolean resuming = false;

    /** Client: connect again after a drop, without resetting anything else. */
    public static void rejoin(String address, int port) throws IOException {
        resuming = true;
        if (toHost != null) toHost.close();
        TcpPeer p = new TcpPeer(new Socket(address, port), 0);
        toHost = p;
        startReader(p);
        logger.info("reconnected to " + address + ":" + port);
    }

    public static void join(String address, int port) throws IOException {
        shutdown();
        host = false;
        TcpPeer p = new TcpPeer(new Socket(address, port), 0);
        toHost = p;
        startReader(p);
        logger.info("joined " + address + ":" + port);
    }

    /** Client over Steam, reconnecting: keep everything else. */
    static void rejoinSteam(Peer hostPeer) {
        resuming = true;
        toHost = hostPeer;
    }

    /** Client over Steam. */
    static void joinSteam(Peer hostPeer) {
        shutdown();
        host = false;
        toHost = hostPeer;
    }

    private static void startReader(TcpPeer p) {
        Thread t = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(p.socket.getInputStream(),
                    StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) received(p, line);
            } catch (Exception e) {
                logger.info("connection closed: " + e);
            }
            lost(p);
        }, "bg-mp-read");
        t.setDaemon(true);
        t.start();
    }

    /** A line arrived from a peer (any thread). */
    static void received(Peer p, String line) {
        JsonObject o = new JsonParser().parse(line).getAsJsonObject();
        String t = o.has("t") ? o.get("t").getAsString() : "";
        if (host) {
            if (t.equals("resume")) {
                resume(p, o.get("id").getAsInt(), o.get("n").getAsLong(), o.has("claim") && o.get("claim").getAsBoolean());
                return;
            }
            if (t.equals("ping")) return;
            if (t.equals("knock")) {
                welcome(p); // Steam: answer with the connection id; the player then says hello or resume
                return;
            }
            synchronized (stampLock) {
                if (!peers.contains(p)) {
                    if (!t.equals("hello")) return; // not live yet: only hello (or resume) lets a connection in
                    peers.add(p);
                }
            }
            stampAndBroadcast(o, p.id);
        } else if (!t.equals("ping")) inbox.add(o);
    }

    /** Host: a player came back. Give them their old id and replay what they missed. */
    private static void resume(Peer p, int oldId, long lastSeen, boolean claimOnly) {
        synchronized (stampLock) {
            if (!claimOnly) {
                // a reconnect: the old connection (if the host still has it) is replaced
                for (Peer q : peers) if (q != p && q.id == oldId) {
                    q.replaced = true;
                    peers.remove(q);
                    q.close();
                }
            }
            p.id = oldId;
            // catch up, then go live, inside the lock so nothing slips between
            for (int i = 0; i < history.size(); i++) if (historyN.get(i) > lastSeen) p.write(history.get(i));
            if (!peers.contains(p)) peers.add(p);
        }
        JsonObject back = new JsonObject();
        back.addProperty("t", "back");
        stampAndBroadcast(back, oldId);
        logger.info("player " + oldId + " reconnected, replayed from " + lastSeen);
    }

    /** A peer went away (any thread). */
    static void lost(Peer p) {
        peers.remove(p);
        if (p.replaced) return;
        JsonObject left = new JsonObject();
        left.addProperty("t", "left");
        if (host) {
            left.addProperty("who", p.id);
            stampAndBroadcast(left, 0);
        } else if (p == toHost) {
            left.addProperty("who", 0);
            inbox.add(left);
            toHost = null;
        }
    }

    private static void stampAndBroadcast(JsonObject o, int from) {
        synchronized (stampLock) {
            o.addProperty("from", from);
            o.addProperty("n", seq.getAndIncrement());
            String line = o.toString();
            String t = o.has("t") ? o.get("t").getAsString() : "";
            if (!t.equals("status")) {
                history.add(line);
                historyN.add(o.get("n").getAsLong());
            }
            for (Peer p : peers) p.write(line);
            inbox.add(o);
        }
    }

    /** Send a message into the shared stream. It comes back through the inbox like everyone else's. */
    public static void send(JsonObject o) {
        if (host) {
            stampAndBroadcast(o, 0);
            return;
        }
        Peer h = toHost;
        String t = o.has("t") ? o.get("t").getAsString() : "";
        boolean control = t.equals("resume") || t.equals("knock") || t.equals("hello") || t.equals("ping");
        if (h != null && (!resuming || control)) h.write(o.toString());
        else if (!t.equals("status") && !t.equals("ping")) synchronized (outbox) {
            outbox.add(o.toString());
        }
    }

    /** Client: a line that could not be written (connection died) is kept for after reconnecting. */
    static void unsent(String line) {
        if (host || line.contains("\"t\":\"status\"") || line.contains("\"t\":\"ping\"")) return;
        synchronized (outbox) {
            outbox.add(line);
        }
    }

    /** Client, after reconnecting and resuming: deliver what was sent while disconnected. */
    public static void flushOutbox() {
        resuming = false;
        Peer h = toHost;
        if (h == null) return;
        synchronized (outbox) {
            for (String line : outbox) h.write(line);
            outbox.clear();
        }
    }

    private static Thread pinger;

    /** Heartbeat on its own thread, so a long load screen doesn't look like a dead connection. */
    private static synchronized void startPinger() {
        if (pinger != null) return;
        pinger = new Thread(() -> {
            String ping = "{\"t\":\"ping\"}";
            while (true) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    return;
                }
                if (host) {
                    for (Peer p : peers) if (p instanceof TcpPeer) p.write(ping);
                } else {
                    Peer h = toHost;
                    if (h instanceof TcpPeer) h.write(ping);
                }
            }
        }, "bg-mp-ping");
        pinger.setDaemon(true);
        pinger.start();
    }

    /** Called every frame on the game thread (Steam packets are polled, not pushed). */
    public static void poll() {
        startPinger();
        if (steamHosting || toHost instanceof SteamNet.SteamPeer) SteamNet.poll();
    }

    /** Test only: close the link to the host as if the network dropped. */
    public static void testDrop() {
        Peer h = toHost;
        if (h != null) h.close();
    }

    public static void shutdown() {
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
        }
        server = null;
        for (Peer p : peers) p.close();
        peers.clear();
        if (toHost != null) toHost.close();
        toHost = null;
        inbox.clear();
        seq.set(0);
        history.clear();
        historyN.clear();
        resuming = false;
        synchronized (outbox) {
            outbox.clear();
        }
        nextPeerId = 1;
        if (steamHosting || SteamNet.inLobby()) SteamNet.leaveLobby();
        steamHosting = false;
    }
}

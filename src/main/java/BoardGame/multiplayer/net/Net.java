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
    static Peer toHost;
    private static final AtomicLong seq = new AtomicLong();
    private static int nextPeerId = 1;
    private static final Object stampLock = new Object();
    private static boolean steamHosting = false;

    /** A connection to another player. */
    abstract static class Peer {
        int id;

        abstract void write(String line);

        abstract void close();
    }

    static class TcpPeer extends Peer {
        final Socket socket;
        final OutputStream out;

        TcpPeer(Socket s, int id) throws IOException {
            this.socket = s;
            this.out = s.getOutputStream();
            this.id = id;
            s.setTcpNoDelay(true);
        }

        @Override
        synchronized void write(String line) {
            try {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                close();
            }
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
    static void welcome(Peer p) {
        JsonObject welcome = new JsonObject();
        welcome.addProperty("t", "welcome");
        welcome.addProperty("id", p.id);
        p.write(welcome.toString());
        peers.add(p);
    }

    public static void join(String address, int port) throws IOException {
        shutdown();
        host = false;
        TcpPeer p = new TcpPeer(new Socket(address, port), 0);
        toHost = p;
        startReader(p);
        logger.info("joined " + address + ":" + port);
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
        if (host) stampAndBroadcast(o, p.id);
        else inbox.add(o);
    }

    /** A peer went away (any thread). */
    static void lost(Peer p) {
        peers.remove(p);
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
            for (Peer p : peers) p.write(line);
            inbox.add(o);
        }
    }

    /** Send a message into the shared stream. It comes back through the inbox like everyone else's. */
    public static void send(JsonObject o) {
        if (host) stampAndBroadcast(o, 0);
        else if (toHost != null) toHost.write(o.toString());
    }

    /** Called every frame on the game thread (Steam packets are polled, not pushed). */
    public static void poll() {
        if (steamHosting || toHost instanceof SteamNet.SteamPeer) SteamNet.poll();
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
        nextPeerId = 1;
        if (steamHosting || SteamNet.inLobby()) SteamNet.leaveLobby();
        steamHosting = false;
    }
}

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
 * Transport: one host, any number of clients, newline-delimited JSON over TCP.
 *
 * The host is a pure sequencer. Every message from anyone (the host's own included)
 * is stamped with the sender id ("from") and a global sequence number ("n") and sent
 * to everybody, the sender included. All game logic runs on that single ordered
 * stream, identically on every machine. Messages land in {@link #inbox} and are
 * consumed on the game thread.
 */
public class Net {
    private static final Logger logger = LogManager.getLogger(Net.class.getName());
    public static final ConcurrentLinkedQueue<JsonObject> inbox = new ConcurrentLinkedQueue<>();

    private static boolean host;
    private static ServerSocket server;
    private static final CopyOnWriteArrayList<Peer> peers = new CopyOnWriteArrayList<>();
    private static Peer toHost;
    private static final AtomicLong seq = new AtomicLong();
    private static int nextPeerId = 1;
    private static final Object stampLock = new Object();

    private static class Peer {
        final Socket socket;
        final OutputStream out;
        final int id;

        Peer(Socket s, int id) throws IOException {
            this.socket = s;
            this.out = s.getOutputStream();
            this.id = id;
            s.setTcpNoDelay(true);
        }

        synchronized void write(String line) {
            try {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                close();
            }
        }

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

    public static boolean connected() {
        return host ? server != null : toHost != null;
    }

    public static void host(int port) throws IOException {
        shutdown();
        host = true;
        server = new ServerSocket(port);
        Thread t = new Thread(() -> {
            while (server != null && !server.isClosed()) {
                try {
                    Socket s = server.accept();
                    Peer p;
                    synchronized (stampLock) {
                        p = new Peer(s, nextPeerId++);
                    }
                    JsonObject welcome = new JsonObject();
                    welcome.addProperty("t", "welcome");
                    welcome.addProperty("id", p.id);
                    p.write(welcome.toString());
                    peers.add(p);
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

    public static void join(String address, int port) throws IOException {
        shutdown();
        host = false;
        toHost = new Peer(new Socket(address, port), 0);
        startReader(toHost);
        logger.info("joined " + address + ":" + port);
    }

    private static void startReader(Peer p) {
        Thread t = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(p.socket.getInputStream(),
                    StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    JsonObject o = new JsonParser().parse(line).getAsJsonObject();
                    if (host) stampAndBroadcast(o, p.id);
                    else inbox.add(o);
                }
            } catch (Exception e) {
                logger.info("connection closed: " + e);
            }
            peers.remove(p);
            JsonObject left = new JsonObject();
            left.addProperty("t", "left");
            if (host) {
                left.addProperty("who", p.id);
                stampAndBroadcast(left, 0);
            } else {
                left.addProperty("who", 0);
                inbox.add(left);
                toHost = null;
            }
        }, "bg-mp-read");
        t.setDaemon(true);
        t.start();
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
    }
}

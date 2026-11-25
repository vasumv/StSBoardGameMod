package BoardGame.multiplayer.net;

import BoardGame.multiplayer.MP;
import BoardGame.multiplayer.Session;
import com.badlogic.gdx.Gdx;
import com.codedisaster.steamworks.*;
import com.megacrit.cardcrawl.core.CardCrawlGame;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/**
 * Playing over Steam, for players on different networks: the host creates a friends-only
 * Steam lobby and invites friends with the Steam overlay; accepting the invite joins the
 * lobby, and messages go over Steam peer-to-peer (Steam punches through NAT or relays).
 * Everyone needs to have the game running through Steam.
 */
public class SteamNet {
    private static final int CHANNEL = 7;
    private static SteamMatchmaking matchmaking;
    private static SteamNetworking networking;
    private static SteamFriends friends;
    private static SteamID lobby;
    private static boolean creating = false;
    private static final HashMap<Long, SteamPeer> byUser = new HashMap<>();
    private static final ByteBuffer readBuf = ByteBuffer.allocateDirect(1 << 20);
    private static final ByteBuffer sendBuf = ByteBuffer.allocateDirect(1 << 20);
    public static String status = "";

    static class SteamPeer extends Net.Peer {
        final SteamID steamId;

        SteamPeer(SteamID s, int id) {
            this.steamId = s;
            this.id = id;
        }

        @Override
        void write(String line) {
            send(steamId, line);
        }

        @Override
        void close() {
            try {
                networking.closeP2PSessionWithUser(steamId);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Is Steam running and initialised for this game? */
    public static boolean available() {
        try {
            boolean running = SteamAPI.isSteamRunning();
            if (!running) Net.logger.error("Steam lobby: SteamAPI.isSteamRunning() is false");
            return running && init();
        } catch (Throwable t) {
            Net.logger.error("Steam lobby: Steam check failed", t);
            return false;
        }
    }

    private static boolean init() {
        if (matchmaking != null) return true;
        try {
            matchmaking = new SteamMatchmaking(new MatchmakingCallbacks());
            networking = new SteamNetworking(new NetworkingCallbacks());
            friends = new SteamFriends(new FriendsCallbacks());
            networking.allowP2PPacketRelay(true);
            return true;
        } catch (Throwable t) {
            Net.logger.error("Steam networking unavailable", t);
            matchmaking = null;
            return false;
        }
    }

    public static String myName() {
        try {
            if (init()) return friends.getPersonaName();
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static boolean inLobby() {
        return lobby != null;
    }

    // ---------------- hosting ----------------

    public static void host() {
        if (!available()) {
            MP.lastError = "Steam isn't available. Start the game through Steam.";
            return;
        }
        if (creating) return;
        creating = true;
        status = "Creating Steam lobby...";
        Net.logger.info("Steam lobby: creating");
        matchmaking.createLobby(SteamMatchmaking.LobbyType.FriendsOnly, 4);
    }

    private static SteamID lastLobby;

    /** Client: join the same lobby again after losing the connection. */
    public static void rejoin() {
        if (lastLobby == null || matchmaking == null) return;
        status = "Reconnecting...";
        matchmaking.joinLobby(lastLobby);
    }

    /** Opens the Steam overlay to invite friends to the lobby. */
    public static void invite() {
        if (lobby != null && friends != null) friends.activateGameOverlayInviteDialog(lobby);
    }

    public static void leaveLobby() {
        if (lobby != null && matchmaking != null) {
            try {
                matchmaking.leaveLobby(lobby);
            } catch (Throwable ignored) {
            }
        }
        lobby = null;
        byUser.clear();
        status = "";
    }

    // ---------------- friends' lobbies ----------------

    /** A Steam friend who is in Slay the Spire and in a lobby. */
    public static class FriendLobby {
        public final String name;
        public final SteamID lobby;

        FriendLobby(String name, SteamID lobby) {
            this.name = name;
            this.lobby = lobby;
        }
    }

    private static final int APP_ID = 646570;
    private static final java.util.ArrayList<FriendLobby> friendLobbies = new java.util.ArrayList<>();
    private static long friendsCheckedAt = 0;

    /** Friends hosting (or in) a lobby right now; refreshed every two seconds. */
    public static java.util.List<FriendLobby> friendLobbies() {
        long now = System.currentTimeMillis();
        if (now - friendsCheckedAt < 2000) return friendLobbies;
        friendsCheckedAt = now;
        friendLobbies.clear();
        if (!available()) return friendLobbies;
        try {
            SteamFriends.FriendGameInfo info = new SteamFriends.FriendGameInfo();
            int n = friends.getFriendCount(SteamFriends.FriendFlags.Immediate);
            for (int i = 0; i < n; i++) {
                SteamID f = friends.getFriendByIndex(i, SteamFriends.FriendFlags.Immediate);
                if (!friends.getFriendGamePlayed(f, info) || info.getGameID() != APP_ID) continue;
                SteamID l = info.getSteamIDLobby();
                if (l == null || !l.isValid() || (lobby != null && l.equals(lobby))) continue;
                friendLobbies.add(new FriendLobby(friends.getFriendPersonaName(f), l));
            }
        } catch (Throwable t) {
            Net.logger.error("Steam: could not list friends' lobbies", t);
        }
        return friendLobbies;
    }

    /** Join a friend's lobby directly (no invite needed). */
    public static void joinFriend(FriendLobby f) {
        if (!available() || MP.inRun) return;
        Net.logger.info("Steam: joining " + f.name + "'s lobby " + f.lobby);
        Net.shutdown();
        status = "Joining " + f.name + "'s lobby...";
        matchmaking.joinLobby(f.lobby);
    }

    // ---------------- packets ----------------

    private static synchronized void send(SteamID to, String line) {
        try {
            byte[] b = line.getBytes(StandardCharsets.UTF_8);
            if (b.length > sendBuf.capacity()) {
                Net.logger.error("message too large for Steam: " + b.length);
                return;
            }
            sendBuf.clear();
            sendBuf.put(b);
            sendBuf.flip();
            // a full send buffer can refuse a packet: retry briefly rather than drop it
            for (int i = 0; i < 50; i++) {
                if (networking.sendP2PPacket(to, sendBuf, SteamNetworking.P2PSend.Reliable, CHANNEL)) return;
                sendBuf.rewind();
                Thread.sleep(10);
            }
            Net.logger.error("Steam refused a packet after retries (" + b.length + " bytes)");
        } catch (Throwable t) {
            Net.logger.error("Steam send failed", t);
        }
    }

    /** Game thread, every frame. */
    static void poll() {
        if (networking == null) return;
        int[] size = new int[1];
        try {
            while (networking.isP2PPacketAvailable(CHANNEL, size)) {
                SteamID from = new SteamID();
                readBuf.clear();
                int n = networking.readP2PPacket(from, readBuf, CHANNEL);
                byte[] b = new byte[n];
                readBuf.get(b, 0, n);
                String line = new String(b, StandardCharsets.UTF_8);
                SteamPeer p = peerFor(from);
                if (p != null) Net.received(p, line);
            }
        } catch (Throwable t) {
            Net.logger.error("Steam read failed", t);
        }
    }

    /** Host: the peer for a Steam user (new players get welcomed). Client: only the host. */
    private static SteamPeer peerFor(SteamID s) {
        long k = SteamNativeHandle.getNativeHandle(s);
        SteamPeer p = byUser.get(k);
        if (p != null) return p;
        if (!Net.isHost()) return null;
        p = new SteamPeer(s, Net.newPeerId());
        byUser.put(k, p);
        Net.welcome(p);
        return p;
    }

    // ---------------- callbacks (Steam's thread: hand over to the game thread) ----------------

    private static class MatchmakingCallbacks implements SteamMatchmakingCallback {
        public void onLobbyCreated(SteamResult result, SteamID id) {
            Gdx.app.postRunnable(() -> {
                creating = false;
                Net.logger.info("Steam lobby: created " + result + " " + id);
                if (result != SteamResult.OK) {
                    status = "";
                    MP.lastError = "Could not create a Steam lobby (" + result + ")";
                    return;
                }
                Net.hostSteam(); // resets any previous session first
                lobby = id;
                matchmaking.setLobbyData(id, "bgmp", "1");
                MP.onSteamHosting();
                status = "Steam lobby ready. Invite friends.";
                invite();
            });
        }

        public void onLobbyEnter(SteamID id, int chatPermissions, boolean blocked,
                                 SteamMatchmaking.ChatRoomEnterResponse response) {
            Gdx.app.postRunnable(() -> {
                Net.logger.info("Steam lobby entered: " + id + " " + response);
                if (Net.isHost() && lobby != null) return; // the host's own entry
                if (response != SteamMatchmaking.ChatRoomEnterResponse.Success) {
                    MP.lastError = "Could not join the Steam lobby (" + response + ")";
                    status = "";
                    return;
                }
                SteamID owner = matchmaking.getLobbyOwner(id);
                SteamPeer hostPeer = new SteamPeer(owner, 0);
                boolean sameRun = MP.reconnecting && MP.inRun && id.equals(lastLobby);
                if (!sameRun) MP.reconnecting = false;
                if (sameRun) Net.rejoinSteam(hostPeer);
                else Net.joinSteam(hostPeer);
                lobby = id;
                lastLobby = id;
                byUser.clear();
                byUser.put(SteamNativeHandle.getNativeHandle(owner), hostPeer);
                status = "Joined " + friends.getFriendPersonaName(owner) + "'s lobby";
                MP.onSteamJoined(); // sends hello, which opens the peer-to-peer session
            });
        }

        public void onLobbyChatUpdate(SteamID id, SteamID changed, SteamID by,
                                      SteamMatchmaking.ChatMemberStateChange state) {
            if (state == SteamMatchmaking.ChatMemberStateChange.Entered) return;
            Gdx.app.postRunnable(() -> {
                SteamPeer p = byUser.remove(SteamNativeHandle.getNativeHandle(changed));
                if (p != null) Net.lost(p);
            });
        }

        public void onLobbyInvite(SteamID a, SteamID b, long c) {
        }

        public void onLobbyDataUpdate(SteamID a, SteamID b, boolean c) {
        }

        public void onLobbyChatMessage(SteamID a, SteamID b, SteamMatchmaking.ChatEntryType c, int d) {
        }

        public void onLobbyGameCreated(SteamID a, SteamID b, int c, short d) {
        }

        public void onLobbyMatchList(int n) {
        }

        public void onLobbyKicked(SteamID a, SteamID b, boolean c) {
        }

        public void onFavoritesListChanged(int a, int b, int c, int d, int e, boolean f, int g) {
        }

        public void onFavoritesListAccountsUpdated(SteamResult r) {
        }
    }

    private static class NetworkingCallbacks implements SteamNetworkingCallback {
        public void onP2PSessionConnectFail(SteamID who, SteamNetworking.P2PSessionError err) {
            Gdx.app.postRunnable(() -> {
                Net.logger.error("Steam P2P connection failed: " + err);
                SteamPeer p = byUser.remove(SteamNativeHandle.getNativeHandle(who));
                if (p != null) Net.lost(p);
            });
        }

        public void onP2PSessionRequest(SteamID who) {
            // only people in our lobby
            networking.acceptP2PSessionWithUser(who);
        }
    }

    private static class FriendsCallbacks implements SteamFriendsCallback {
        /** A friend's invite was accepted in the Steam overlay while the game is running. */
        public void onGameLobbyJoinRequested(SteamID lobbyId, SteamID friend) {
            Gdx.app.postRunnable(() -> {
                Net.logger.info("Steam invite accepted: lobby " + lobbyId + " from " + friend);
                if (MP.inRun) {
                    MP.lastError = "Finish or leave your current run to join a lobby";
                    return;
                }
                Net.shutdown();
                BoardGame.multiplayer.ui.LobbyScreen.open = true; // show the lobby while joining
                status = "Joining " + friends.getFriendPersonaName(friend) + "'s lobby...";
                matchmaking.joinLobby(lobbyId);
            });
        }

        public void onPersonaStateChange(SteamID a, SteamFriends.PersonaChange b) {
        }

        public void onGameOverlayActivated(boolean a) {
        }

        public void onAvatarImageLoaded(SteamID a, int b, int c, int d) {
        }

        public void onFriendRichPresenceUpdate(SteamID a, int b) {
        }

        public void onGameRichPresenceJoinRequested(SteamID a, String b) {
        }

        public void onGameServerChangeRequested(String a, String b) {
        }

        public void onSetPersonaNameResponse(boolean a, boolean b, SteamResult c) {
        }
    }

    /** Steam shows "Join game" for an invite received before launching: handle +connect_lobby. */
    public static void checkLaunchArgs() {
        boolean ok = available(); // registers the invite/lobby listeners
        Net.logger.info("Steam invites: " + (ok ? "listening" : "unavailable"));
        String arg = System.getProperty("sun.java.command", "");
        int i = arg.indexOf("+connect_lobby");
        if (i < 0 || !ok) return;
        try {
            String id = arg.substring(i + "+connect_lobby".length()).trim().split("\\s+")[0];
            matchmaking.joinLobby(SteamID.createFromNativeHandle(Long.parseLong(id)));
            status = "Joining lobby...";
        } catch (Throwable ignored) {
        }
    }
}

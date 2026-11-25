package BoardGame.multiplayer.ui;

import BoardGame.multiplayer.MP;
import BoardGame.multiplayer.Session;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.input.InputHelper;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Multiplayer lobby on the main menu: a "Multiplayer" button opens a panel to host or
 * join a game, pick your character, and (host) pick the ascension level and start.
 */
public class LobbyScreen {
    private static final Color SHADE = new Color(0f, 0f, 0f, 0.6f);
    private static final Color PANEL = new Color(0.08f, 0.1f, 0.13f, 0.95f);
    private static final Color BTN = new Color(0.18f, 0.22f, 0.28f, 1f);
    private static final Color BTN_HOVER = new Color(0.3f, 0.4f, 0.5f, 1f);
    private static final Color BTN_ON = new Color(0.25f, 0.45f, 0.25f, 1f);
    private static final Color FIELD = new Color(0.02f, 0.02f, 0.03f, 1f);

    public static boolean open = false;
    private static String address = "";
    private static boolean fieldFocused = true;
    private static ArrayList<String> myAddresses;
    private static float cursorBlink = 0f;

    // ---- layout helpers (1080p units from the top-left) ----
    private static final float PX = 480, PY = 170, PW = 960, PH = 680;

    private static float sx(float x) { return x * Settings.scale; }
    private static float sy(float yTop, float h) { return Settings.HEIGHT - (yTop + h) * Settings.scale; }

    private static boolean hovered(float x, float y, float w, float h) {
        float mx = InputHelper.mX, my = InputHelper.mY;
        return mx >= sx(x) && mx <= sx(x + w) && my >= sy(y, h) && my <= sy(y, h) + h * Settings.scale;
    }

    private static boolean button(SpriteBatch sb, boolean clicked, float x, float y, float w, float h, String text,
                                  boolean on) {
        boolean hov = hovered(x, y, w, h);
        if (sb != null) {
            rect(sb, x, y, w, h, on ? BTN_ON : hov ? BTN_HOVER : BTN);
            FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, text, sx(x + w / 2), sy(y, h) + h * Settings.scale / 2,
                    Settings.GOLD_COLOR);
        }
        return clicked && hov;
    }

    private static void rect(SpriteBatch sb, float x, float y, float w, float h, Color c) {
        sb.setColor(c);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, sx(x), sy(y, h), w * Settings.scale, h * Settings.scale);
        sb.setColor(Color.WHITE);
    }

    private static void text(SpriteBatch sb, String s, float x, float y, Color c) {
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, s, sx(x), Settings.HEIGHT - y * Settings.scale, c);
    }

    private static int port() {
        return Integer.getInteger("bg.mp.port", 7777);
    }

    private static String profileName() {
        String n = CardCrawlGame.playerName;
        return n == null || n.isEmpty() ? "Player" : n;
    }

    private static ArrayList<String> localAddresses() {
        if (myAddresses != null) return myAddresses;
        myAddresses = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (a instanceof Inet4Address) myAddresses.add(a.getHostAddress() + ":" + port());
            }
        } catch (Exception ignored) {
        }
        return myAddresses;
    }

    // ---------------- one function draws (sb != null) or handles a click (sb == null) ----------------

    /** The "Multiplayer" button on the main menu, and the panel when open. */
    public static void frame(SpriteBatch sb, boolean clicked) {
        if (!open) {
            String label = MP.active ? "Multiplayer lobby (" + MP.players.size() + ")" : "Multiplayer";
            if (button(sb, clicked, 1500, 130, 360, 56, label, MP.active)) open = true;
            return;
        }
        if (sb != null) {
            sb.setColor(SHADE);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0, 0, Settings.WIDTH, Settings.HEIGHT);
            rect(sb, PX, PY, PW, PH, PANEL);
            FontHelper.renderFontCentered(sb, FontHelper.bannerNameFont, "Multiplayer", sx(PX + PW / 2),
                    Settings.HEIGHT - (PY + 40) * Settings.scale, Settings.GOLD_COLOR);
        }
        float x = PX + 40, y = PY + 90;
        if (!MP.active) {
            if (sb != null) text(sb, "Playing as " + profileName() + ". Everyone needs the same BoardGame.jar.", x, y,
                    Settings.CREAM_COLOR);
            y += 50;
            if (sb != null) text(sb, "Over the internet (Steam): host, then invite friends from the Steam overlay."
                    + " Friends just accept the invite.", x, y, Settings.GOLD_COLOR);
            if (button(sb, clicked, x, y + 30, 300, 56, "Host Steam lobby", false)) {
                MP.lastError = "";
                BoardGame.multiplayer.net.SteamNet.host();
            }
            if (sb != null && !BoardGame.multiplayer.net.SteamNet.status.isEmpty())
                text(sb, BoardGame.multiplayer.net.SteamNet.status, x + 320, y + 46, Settings.CREAM_COLOR);
            y += 110;
            if (sb != null) text(sb, "Same network, Tailscale or a forwarded port: host by address", x, y,
                    Settings.GOLD_COLOR);
            if (button(sb, clicked, x, y + 30, 300, 56, "Host on port " + port(), false)) {
                MP.myName = profileName();
                if (!MP.host(port())) Session.toast(MP.lastError);
            }
            y += 110;
            if (sb != null) {
                text(sb, "Join a game: type the host's address (e.g. 192.168.1.20:" + port() + ")", x, y, Settings.GOLD_COLOR);
                rect(sb, x, y + 30, 560, 56, fieldFocused ? new Color(0.1f, 0.1f, 0.14f, 1f) : FIELD);
                boolean showCursor = fieldFocused && (cursorBlink % 1f) < 0.5f;
                FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, address + (showCursor ? "_" : ""),
                        sx(x + 14), Settings.HEIGHT - (y + 46) * Settings.scale, Settings.CREAM_COLOR);
            }
            if (clicked && hovered(x, y + 30, 560, 56)) fieldFocused = true;
            if (button(sb, clicked, x + 580, y + 30, 140, 56, "Paste", false)) {
                try {
                    String c = Gdx.app.getClipboard().getContents();
                    if (c != null) address = c.trim();
                } catch (Throwable ignored) {
                }
            }
            if (button(sb, clicked, x + 740, y + 30, 140, 56, "Join", false)) join();
            y += 110;
            if (sb != null && MP.connecting) text(sb, "Connecting to " + address + " ...", x, y, Settings.CREAM_COLOR);
            else if (sb != null && !MP.lastError.isEmpty()) text(sb, MP.lastError, x, y, Settings.RED_TEXT_COLOR);
            if (button(sb, clicked, PX + PW - 200, PY + PH - 80, 160, 56, "Close", false)) open = false;
            return;
        }
        // ---- connected ----
        if (sb != null) {
            if (MP.isHost() && BoardGame.multiplayer.net.Net.usingSteam()) {
                text(sb, "Hosting a Steam lobby. Invite friends; they accept the invite in Steam.", x, y,
                        Settings.CREAM_COLOR);
            } else if (MP.isHost()) {
                text(sb, "You are hosting. Friends join with one of these addresses:", x, y, Settings.CREAM_COLOR);
                text(sb, String.join("    ", localAddresses()), x, y + 28, Settings.GOLD_COLOR);
            } else {
                text(sb, "Connected. The host picks the ascension and starts the run.", x, y, Settings.CREAM_COLOR);
            }
        }
        if (MP.isHost() && BoardGame.multiplayer.net.Net.usingSteam()
                && button(sb, clicked, PX + PW - 300, y - 6, 260, 50, "Invite Steam friends", false))
            BoardGame.multiplayer.net.SteamNet.invite();
        y += 80;
        if (sb != null) text(sb, "Players", x, y, Settings.GOLD_COLOR);
        y += 30;
        for (MP.Player p : MP.players) {
            if (sb != null) text(sb, (p.id == MP.myId ? "> " : "   ") + p.name + "  -  " + MP.CHARACTER_NAMES[p.character]
                    + (p.id == 0 ? "  (host)" : ""), x, y, p.id == MP.myId ? Settings.GREEN_TEXT_COLOR : Settings.CREAM_COLOR);
            y += 28;
        }
        y = Math.max(y + 20, PY + 330);
        if (sb != null) text(sb, "Your character", x, y, Settings.GOLD_COLOR);
        for (int i = 0; i < MP.CHARACTER_NAMES.length; i++) {
            if (button(sb, clicked, x + i * 210, y + 30, 195, 56, MP.CHARACTER_NAMES[i], MP.myCharacter == i))
                MP.setCharacter(i);
        }
        y += 110;
        if (sb != null) text(sb, "Ascension", x, y, Settings.GOLD_COLOR);
        if (MP.isHost()) {
            if (button(sb, clicked, x, y + 30, 60, 56, "-", false)) MP.setAscension(MP.ascension - 1);
            if (sb != null) FontHelper.renderFontCentered(sb, FontHelper.bannerNameFont, String.valueOf(MP.ascension),
                    sx(x + 110), sy(y + 30, 56) + 28 * Settings.scale, Settings.GOLD_COLOR);
            if (button(sb, clicked, x + 160, y + 30, 60, 56, "+", false)) MP.setAscension(MP.ascension + 1);
            if (sb != null) text(sb, MP.ascension == 0 ? "(off)" : "", x + 240, y + 46, Settings.CREAM_COLOR);
        } else if (sb != null) {
            text(sb, MP.ascension == 0 ? "Off" : "Level " + MP.ascension, x, y + 40, Settings.CREAM_COLOR);
        }
        if (MP.isHost() && button(sb, clicked, PX + PW - 420, PY + PH - 80, 200, 56, "Start run", false)) {
            open = false;
            MP.startRun(System.nanoTime());
        }
        if (button(sb, clicked, PX + PW - 200, PY + PH - 80, 160, 56, "Leave", false)) MP.leave();
        if (button(sb, clicked, PX + 40, PY + PH - 80, 160, 56, "Hide", false)) open = false;
    }

    private static void join() {
        String t = address.trim();
        if (t.isEmpty()) {
            MP.lastError = "Type the host's address first";
            return;
        }
        int p = port();
        String host = t;
        if (t.contains(":")) {
            host = t.substring(0, t.lastIndexOf(':'));
            try {
                p = Integer.parseInt(t.substring(t.lastIndexOf(':') + 1));
            } catch (NumberFormatException e) {
                MP.lastError = "The port after ':' must be a number";
                return;
            }
        }
        MP.myName = profileName();
        MP.joinAsync(host, p);
    }

    private static com.badlogic.gdx.InputProcessor previous;
    private static final com.badlogic.gdx.InputAdapter keys = new com.badlogic.gdx.InputAdapter() {
        @Override
        public boolean keyTyped(char c) {
            if (!open || MP.active || !fieldFocused) return false;
            if (c == '\b') {
                if (!address.isEmpty()) address = address.substring(0, address.length() - 1);
            } else if (c == '\r' || c == '\n') {
                Gdx.app.postRunnable(LobbyScreen::join);
            } else if (c == ':' || c == ';') {
                if (!colonThisFrame) address += ":";
                colonThisFrame = true;
            } else if (c >= 32 && c < 127 && address.length() < 60) {
                address += c;
            }
            return false;
        }
    };

    private static boolean colonThisFrame = false;

    /** Typing into the address field: real key-typed events, so fast typing and pasting by keyboard work. */
    public static void typing() {
        cursorBlink += Gdx.graphics.getDeltaTime();
        // some keyboard setups never deliver ':' as a typed character; catch the key itself
        if (open && !MP.active && fieldFocused && !colonThisFrame
                && (Gdx.input.isKeyJustPressed(Input.Keys.SEMICOLON) || Gdx.input.isKeyJustPressed(Input.Keys.COLON)))
            address += ":";
        colonThisFrame = false;
        boolean want = open && !MP.active;
        com.badlogic.gdx.InputProcessor cur = Gdx.input.getInputProcessor();
        if (want && previous == null) {
            previous = cur;
            com.badlogic.gdx.InputMultiplexer mux = new com.badlogic.gdx.InputMultiplexer(keys);
            if (cur != null) mux.addProcessor(cur);
            Gdx.input.setInputProcessor(mux);
        } else if (!want && previous != null) {
            Gdx.input.setInputProcessor(previous);
            previous = null;
        }
    }

    /** While the panel is open, the main menu underneath ignores input. */
    @com.evacipated.cardcrawl.modthespire.lib.SpirePatch2(
            clz = com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen.class, method = "update")
    public static class BlockMenu {
        @com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch
        public static com.evacipated.cardcrawl.modthespire.lib.SpireReturn<Void> prefix() {
            if (open) {
                InputHelper.justClickedLeft = false;
                InputHelper.justReleasedClickLeft = false;
                return com.evacipated.cardcrawl.modthespire.lib.SpireReturn.Return();
            }
            return com.evacipated.cardcrawl.modthespire.lib.SpireReturn.Continue();
        }
    }
}

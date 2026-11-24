package BoardGame.multiplayer.ui;

import BoardGame.multiplayer.MP;
import BoardGame.multiplayer.Session;
import BoardGame.multiplayer.combat.MonsterSync;
import basemod.interfaces.PostRenderSubscriber;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

import java.util.ArrayList;

/**
 * Multiplayer UI drawn over the game: lobby on the main menu, teammates and the play
 * queue in combat, the rollback vote, map votes and short notices.
 */
public class MPOverlay implements PostRenderSubscriber {
    private static final Color BG = new Color(0f, 0f, 0f, 0.7f);
    private static final Color BG_HOVER = new Color(0.25f, 0.35f, 0.45f, 0.95f);
    private static boolean wasDown = false;
    private static boolean clicked = false;

    /** A clickable text button laid out in 1080p units from the top-left. */
    private static class Button {
        float x, y, w, h;
        String text;

        Button(float x, float y, float w, float h, String text) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.text = text;
        }

        float sx() { return x * Settings.scale; }
        float sy() { return Settings.HEIGHT - (y + h) * Settings.scale; }
        float sw() { return w * Settings.scale; }
        float sh() { return h * Settings.scale; }

        boolean hovered() {
            float mx = InputHelper.mX, my = InputHelper.mY;
            return mx >= sx() && mx <= sx() + sw() && my >= sy() && my <= sy() + sh();
        }

        boolean clicked() {
            return clicked && hovered();
        }

        void render(SpriteBatch sb) {
            box(sb, sx(), sy(), sw(), sh(), hovered() ? BG_HOVER : BG);
            FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, text, sx() + sw() / 2f, sy() + sh() / 2f,
                    Settings.GOLD_COLOR);
        }
    }

    // lobby
    private static final Button hostBtn = new Button(1460, 120, 420, 48, "Host multiplayer game");
    private static final Button joinBtn = new Button(1460, 176, 420, 48, "Join multiplayer game");
    private static final Button charBtn = new Button(1460, 120, 420, 48, "");
    private static final Button startBtn = new Button(1460, 176, 420, 48, "Start run");
    private static final Button leaveBtn = new Button(1460, 232, 420, 48, "Leave");
    // rollback vote
    private static final Button acceptBtn = new Button(760, 520, 190, 50, "Accept");
    private static final Button declineBtn = new Button(970, 520, 190, 50, "Decline");

    private static boolean onMainMenu() {
        return CardCrawlGame.mode == CardCrawlGame.GameMode.CHAR_SELECT && CardCrawlGame.mainMenuScreen != null
                && !CardCrawlGame.isInARun() && CardCrawlGame.mainMenuScreen.screen
                == com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen.CurScreen.MAIN_MENU;
    }

    public static void update() {
        boolean down = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        clicked = down && !wasDown;
        wasDown = down;
        if (!clicked) return;
        if (onMainMenu()) {
            if (!MP.active) {
                if (hostBtn.clicked()) {
                    MP.myName = profileName();
                    if (!MP.host(port())) Session.toast(MP.lastError);
                } else if (joinBtn.clicked()) {
                    askAddress();
                }
            } else if (!MP.inRun) {
                if (charBtn.clicked()) MP.setCharacter((MP.myCharacter + 1) % MP.CHARACTER_NAMES.length);
                else if (MP.isHost() && startBtn.clicked()) MP.startRun(System.nanoTime());
                else if (leaveBtn.clicked()) MP.leave();
            }
            return;
        }
        if (Session.proposal != null && Session.proposal.proposer != MP.myId
                && !Session.proposal.yes.contains(MP.myId)) {
            if (acceptBtn.clicked()) Session.voteRollback(true);
            else if (declineBtn.clicked()) Session.voteRollback(false);
        }
    }

    private static int port() {
        return Integer.getInteger("bg.mp.port", 7777);
    }

    private static String profileName() {
        String n = CardCrawlGame.playerName;
        return n == null || n.isEmpty() ? "Player" : n;
    }

    private static void askAddress() {
        Gdx.input.getTextInput(new Input.TextInputListener() {
            @Override
            public void input(String text) {
                String t = text.trim();
                int p = port();
                String host = t;
                if (t.contains(":")) {
                    host = t.substring(0, t.lastIndexOf(':'));
                    try {
                        p = Integer.parseInt(t.substring(t.lastIndexOf(':') + 1));
                    } catch (NumberFormatException ignored) {
                    }
                }
                final String h = host;
                final int pp = p;
                Gdx.app.postRunnable(() -> {
                    MP.myName = profileName();
                    if (!MP.join(h, pp)) Session.toast(MP.lastError);
                });
            }

            @Override
            public void canceled() {
            }
        }, "Join multiplayer game", "127.0.0.1:" + port(), "host address:port");
    }

    @Override
    public void receivePostRender(SpriteBatch sb) {
        try {
            render(sb);
        } catch (Throwable t) {
            MP.logger.error("overlay render", t);
        }
        sb.setColor(Color.WHITE);
    }

    private static void render(SpriteBatch sb) {
        if (onMainMenu()) renderLobby(sb);
        else if (MP.inRun && CardCrawlGame.isInARun() && AbstractDungeon.player != null) renderRun(sb);
        renderToasts(sb);
    }

    private static void renderLobby(SpriteBatch sb) {
        if (!MP.active) {
            hostBtn.render(sb);
            joinBtn.render(sb);
            return;
        }
        charBtn.text = "Your character: " + MP.CHARACTER_NAMES[MP.myCharacter] + "  (click to change)";
        charBtn.render(sb);
        if (MP.isHost()) startBtn.render(sb);
        leaveBtn.render(sb);
        ArrayList<String> lines = new ArrayList<>();
        lines.add(MP.isHost() ? "Hosting on port " + port() : "Joined. Waiting for the host to start.");
        for (MP.Player p : MP.players)
            lines.add((p.id == MP.myId ? "> " : "  ") + p.name + " - " + MP.CHARACTER_NAMES[p.character]
                    + (p.id == 0 ? " (host)" : ""));
        textBlock(sb, 1460, 300, 420, lines, Settings.CREAM_COLOR);
    }

    private static void renderRun(SpriteBatch sb) {
        // teammates, top right under the relic bar
        ArrayList<String> lines = new ArrayList<>();
        boolean combat = MonsterSync.active();
        for (MP.Player p : MP.players) {
            if (p.id == MP.myId) continue;
            String s = p.name + " (" + MP.CHARACTER_NAMES[p.character] + ", row " + (p.row + 1) + ")  HP " + p.hp
                    + "/" + p.maxHp;
            if (combat) s += "  Block " + p.block + "  Energy " + p.energy + "  Hand " + p.handSize
                    + (p.endTurnReady ? "  [READY]" : "");
            else s += "  Gold " + p.gold;
            if (!p.connected) s += "  (disconnected)";
            lines.add(s);
            if (combat && p.powers != null && !p.powers.isEmpty()) lines.add("    " + p.powers);
        }
        if (combat) {
            MP.Player me = MP.me();
            lines.add("You: row " + (MP.myRow() + 1) + (me != null && me.endTurnReady
                    ? "  [READY - click End Turn again to cancel]" : ""));
            String r = Session.resolvingLabel();
            if (r != null) lines.add("Playing: " + r);
            for (String q : Session.queuedLabels()) lines.add("Queued: " + q);
            if (Session.endTurnPending) lines.add("Ending turn...");
            else if (!Session.eotReleased && Session.eotSent) lines.add("Waiting for everyone's end of turn...");
        }
        if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP && !Session.mapVotes.isEmpty()) {
            lines.add("Path votes (everyone must pick the same room):");
            for (MP.Player p : MP.players) {
                int[] v = Session.mapVotes.get(p.id);
                lines.add("  " + p.name + ": " + (v == null ? "-" : roomName(v[0], v[1])));
            }
        }
        textBlock(sb, 1180, 110, 720, lines, Settings.CREAM_COLOR);

        if (combat) renderRowLabels(sb);
        if (Session.proposal != null) renderProposal(sb);
    }

    private static String roomName(int x, int y) {
        try {
            return AbstractDungeon.map.get(y).get(x).getRoom().getClass().getSimpleName().replace("Room", "")
                    + " (floor " + (y + 1) + ")";
        } catch (Throwable t) {
            return x + "," + y;
        }
    }

    private static void renderRowLabels(SpriteBatch sb) {
        if (MonsterSync.isBossRoom() || MP.playerCount() < 2 || AbstractDungeon.getCurrRoom().monsters == null) return;
        for (MP.Player p : MP.players) {
            float minX = Float.MAX_VALUE, y = 0;
            boolean any = false;
            for (AbstractMonster m : AbstractDungeon.getCurrRoom().monsters.monsters) {
                if (m.isDeadOrEscaped() || MonsterSync.row(m) != p.row) continue;
                any = true;
                minX = Math.min(minX, m.hb.x);
                y = m.hb.y + m.hb.height + 60f * Settings.scale;
            }
            if (!any) continue;
            String label = p.id == MP.myId ? "Your row" : p.name + "'s row";
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, label, minX, y,
                    p.id == MP.myId ? Settings.GREEN_TEXT_COLOR : Settings.BLUE_TEXT_COLOR);
        }
    }

    private static void renderProposal(SpriteBatch sb) {
        Session.Proposal pr = Session.proposal;
        float x = 700 * Settings.scale, w = 520 * Settings.scale;
        float top = Settings.HEIGHT - 400 * Settings.scale, h = 200 * Settings.scale;
        box(sb, x, top - h, w, h, BG);
        ArrayList<String> waiting = new ArrayList<>();
        for (MP.Player p : MP.players) if (p.connected && !pr.yes.contains(p.id)) waiting.add(p.name);
        FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, Session.nameOf(pr.proposer)
                + " wants to undo back to before:", x + w / 2f, top - 30 * Settings.scale, Settings.CREAM_COLOR);
        FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, pr.label, x + w / 2f, top - 62 * Settings.scale,
                Settings.GOLD_COLOR);
        FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, "Waiting for: " + String.join(", ", waiting),
                x + w / 2f, top - 94 * Settings.scale, Settings.CREAM_COLOR);
        if (pr.proposer != MP.myId && !pr.yes.contains(MP.myId)) {
            acceptBtn.render(sb);
            declineBtn.render(sb);
        }
    }

    private static void renderToasts(SpriteBatch sb) {
        float y = Settings.HEIGHT - 260 * Settings.scale;
        for (String t : Session.toasts) {
            FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, t, Settings.WIDTH / 2f, y, Settings.GOLD_COLOR);
            y -= 34 * Settings.scale;
        }
    }

    private static void textBlock(SpriteBatch sb, float x1080, float yTop1080, float w1080, ArrayList<String> lines,
                                  Color c) {
        if (lines.isEmpty()) return;
        float lh = 26f * Settings.scale;
        float x = x1080 * Settings.scale, w = w1080 * Settings.scale;
        float top = Settings.HEIGHT - yTop1080 * Settings.scale;
        box(sb, x - 8 * Settings.scale, top - lh * lines.size() - 12 * Settings.scale, w, lh * lines.size()
                + 16 * Settings.scale, BG);
        float y = top;
        for (String l : lines) {
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, l, x, y - 4 * Settings.scale, c);
            y -= lh;
        }
    }

    private static void box(SpriteBatch sb, float x, float y, float w, float h, Color c) {
        sb.setColor(c);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x, y, w, h);
        sb.setColor(Color.WHITE);
    }
}

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

    /** A fresh left click this frame that nothing else has used (for other overlay panels). */
    /** Is there an unused click this frame (without using it)? */
    public static boolean peekClick() {
        return clicked;
    }

    public static boolean takeClick() {
        if (!clicked) return false;
        clicked = false;
        return true;
    }

    /** A clickable text button laid out in 1080p units from the top-left. */
    private static class Button {
        float x, y, w, h;
        String text;
        Color fill; // optional: a coloured button (Accept / Decline)

        Button(float x, float y, float w, float h, String text, Color fill) {
            this(x, y, w, h, text);
            this.fill = fill;
        }

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
            Color c = fill == null ? (hovered() ? BG_HOVER : BG)
                    : hovered() ? new Color(Math.min(1f, fill.r + 0.15f), Math.min(1f, fill.g + 0.15f),
                    Math.min(1f, fill.b + 0.15f), 1f) : fill;
            box(sb, sx(), sy(), sw(), sh(), c);
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
    // die
    private static final Button keepBtn = new Button(36, 260, 160, 38, "");
    // rollback vote
    private static final Button acceptBtn = new Button(760, 520, 190, 50, "Accept", new Color(0.15f, 0.45f, 0.2f, 1f));
    private static final Button declineBtn = new Button(970, 520, 190, 50, "Decline", new Color(0.5f, 0.15f, 0.15f, 1f));

    // party actions (built fresh each frame; the same list is used for clicks and drawing)
    private interface Act {
        void run();
    }

    private static final ArrayList<Button> partyButtons = new ArrayList<>();
    private static final ArrayList<Act> partyActs = new ArrayList<>();

    private static void buildPartyButtons() {
        partyButtons.clear();
        partyActs.clear();
        if (!MP.inRun || MP.playerCount() < 2 || !CardCrawlGame.isInARun() || AbstractDungeon.player == null) return;
        if (AbstractDungeon.currMapNode == null || AbstractDungeon.getCurrRoom() == null) return;
        float y = 170;
        if (MonsterSync.active()) return; // in a fight, drag 'any player' cards onto a teammate's panel
        if (AbstractDungeon.getCurrRoom().phase == com.megacrit.cardcrawl.rooms.AbstractRoom.RoomPhase.COMBAT) return;
        boolean shopOrEvent = AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.ShopRoom
                || AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.EventRoom;
        for (MP.Player p : MP.players) {
            if (p.id == MP.myId || !p.connected) continue;
            final int to = p.id;
            add(16, y, "Switch rows with " + p.name, () -> Session.swapRow(to));
            y += 40;
            for (int i = 0; i < AbstractDungeon.player.potions.size(); i++) {
                com.megacrit.cardcrawl.potions.AbstractPotion po = AbstractDungeon.player.potions.get(i);
                if (po instanceof com.megacrit.cardcrawl.potions.PotionSlot) continue;
                final int slot = i;
                add(16, y, "Give " + po.name + " to " + p.name, () -> Session.givePotion(slot, to));
                y += 40;
            }
            if (shopOrEvent && AbstractDungeon.player.gold > 0) {
                add(16, y, "Pay 1 gold toward " + p.name + "'s purchase", () -> Session.giveGold(1, to));
                y += 40;
            }
        }
    }

    private static void add(float x, float y, String text, Act a) {
        partyButtons.add(new Button(x, y, 420, 36, text));
        partyActs.add(a);
    }

    private static boolean onMainMenu() {
        return CardCrawlGame.mode == CardCrawlGame.GameMode.CHAR_SELECT && CardCrawlGame.mainMenuScreen != null
                && !CardCrawlGame.isInARun() && CardCrawlGame.mainMenuScreen.screen
                == com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen.CurScreen.MAIN_MENU;
    }

    public static void update() {
        RelicPicker.update();
        ChoicePrompt.update();
        PartyEventUI.update();
        EventDie.update();
        BoardGame.multiplayer.combat.DiePhase.autoKeep();
        placeDieButtons();
        BoardGame.multiplayer.combat.IntentDetails.update();
        if (BoardGame.multiplayer.combat.DiePhase.rollAnim > 0)
            BoardGame.multiplayer.combat.DiePhase.rollAnim -= Gdx.graphics.getDeltaTime();
        boolean down = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        clicked = down && !wasDown;
        wasDown = down;
        PartyPanel.update();
        if (onMainMenu()) LobbyScreen.typing();
        if (!clicked) return;
        if (onMainMenu()) {
            LobbyScreen.frame(null, true);
            return;
        }
        if (false) {
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
        if (BoardGame.multiplayer.combat.DiePhase.deciding() && !BoardGame.multiplayer.combat.DiePhase.iConfirmed()
                && !acceptRollShown() && keepBtn.clicked()) {
            BoardGame.multiplayer.combat.DiePhase.confirm();
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
        if (onMainMenu()) LobbyScreen.frame(sb, false);
        else if (MP.inRun && CardCrawlGame.isInARun() && AbstractDungeon.player != null) renderRun(sb);
        EventDie.render(sb); // under any prompt it leads to
        RelicPicker.render(sb);
        ChoicePrompt.render(sb);
        PartyEventUI.render(sb);
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
            if (p.id == MP.myId || combat) continue; // in a fight, teammates have their own panels
            String s = p.name + " (" + MP.CHARACTER_NAMES[p.character] + ", row " + (p.row + 1) + ")  HP " + p.hp
                    + "/" + p.maxHp;
            if (combat) s += "  Block " + p.block + "  Energy " + p.energy + "  Hand " + p.handSize
                    + (p.endTurnReady ? "  [READY]" : "");
            else s += "  Gold " + p.gold;
            if (p.dead) s = p.name + " (" + MP.CHARACTER_NAMES[p.character] + ")  fallen";
            if (!p.connected) s += "  (disconnected)";
            lines.add(s);
            if (combat && p.powers != null && !p.powers.isEmpty()) lines.add("    " + p.powers);
        }
        if (combat) {
            MP.Player me = MP.me();
            lines.add("You" + (me != null && me.endTurnReady ? ": READY (click End Turn again to cancel)" : ""));
            if (false) lines.add("You: row " + (MP.myRow() + 1) + (me != null && me.endTurnReady
                    ? "  [READY - click End Turn again to cancel]" : ""));
            MP.Player actor = Session.currentActor();
            if (actor != null) lines.add(actor.id == MP.myId ? "Your turn (Sequential Turns)" : actor.name + "'s turn");
            String r = Session.resolvingLabel();
            if (r != null) lines.add("Playing: " + r);
            for (String q : Session.queuedLabels()) lines.add("Queued: " + q);
            if (Session.endTurnPending) lines.add("Ending turn...");
            else if (!Session.eotReleased && Session.eotSent) lines.add("Waiting for everyone's end of turn...");
        }
        if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP && !Session.mapVotes.isEmpty()) {
            lines.add("Path votes (most votes wins; a tie is decided by the die):");
            for (MP.Player p : MP.players) {
                int[] v = Session.mapVotes.get(p.id);
                lines.add("  " + p.name + ": " + (v == null ? "-" : roomName(v[0], v[1])));
            }
        }
        if (combat && fullScreenUp()) {
            // a full screen (deck, map, card choice) is open: keep the combat overlay out of its way
        } else if (combat) {
            RowView.render(sb);
            dieBottom1080 = DIE_TOP - 10;
            if (!BoardGame.multiplayer.undo.HistoryPanel.isOpen()) { // the open History list uses this space
                renderDie(sb);
                textBlock(sb, 38, dieBottom1080 + 14, DIE_W - 16, lines, Settings.CREAM_COLOR); // under the die
            }
        } else if (!AbstractDungeon.isScreenUp || AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP) {
            // (hidden behind full screens like card rewards and the deck view; shown on the map for votes)
            float tw = 0;
            for (String l : lines) tw = Math.max(tw, FontHelper.getSmartWidth(FontHelper.tipBodyFont, l, 9999f, 0f));
            float w1080 = tw / Settings.scale + 24;
            textBlock(sb, 24, 232, w1080, lines, Settings.CREAM_COLOR); // left, under the Party button
        }
        PartyPanel.render(sb); // gold, potions and rows: one "Party" button out of combat
        if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP && !Session.mapVotes.isEmpty()) renderVoteMarks(sb);
        if (Session.proposal != null) renderProposal(sb);
    }

    private static final java.util.IdentityHashMap<Object, Float> dieButtonHome = new java.util.IdentityHashMap<>();

    /**
     * The board game's die buttons (Accept Roll, The Abacus, Toolbox, reroll, Gambler's Brew) sit
     * just above the middle of the screen, where the teammate panels are in a shared fight: move
     * them down below the panels while there are panels.
     */
    private static void placeDieButtons() {
        try {
            if (AbstractDungeon.overlayMenu == null) return;
            boolean shift = RowView.enabled();
            com.megacrit.cardcrawl.ui.buttons.Button[] bs = {
                    BoardGame.ui.OverlayMenuPatches.OverlayMenuExtraInterface.lockinrollbutton.get(AbstractDungeon.overlayMenu),
                    BoardGame.ui.OverlayMenuPatches.OverlayMenuExtraInterface.rerollbutton.get(AbstractDungeon.overlayMenu),
                    BoardGame.ui.OverlayMenuPatches.OverlayMenuExtraInterface.theabacusbutton.get(AbstractDungeon.overlayMenu),
                    BoardGame.ui.OverlayMenuPatches.OverlayMenuExtraInterface.toolboxbutton.get(AbstractDungeon.overlayMenu),
                    BoardGame.ui.OverlayMenuPatches.OverlayMenuExtraInterface.potionbutton.get(AbstractDungeon.overlayMenu)};
            for (com.megacrit.cardcrawl.ui.buttons.Button b : bs) {
                if (b == null) continue;
                Float home = dieButtonHome.get(b);
                if (home == null) {
                    home = b.y;
                    dieButtonHome.put(b, home);
                }
                float want = shift ? home - 150f * Settings.scale : home;
                if (b.y != want) {
                    b.y = want;
                    ((com.megacrit.cardcrawl.helpers.Hitbox) basemod.ReflectionHacks.getPrivate(b,
                            com.megacrit.cardcrawl.ui.buttons.Button.class, "hb")).translate(b.x, want);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** On the map: each player's name above the room they voted for. */
    private static void renderVoteMarks(SpriteBatch sb) {
        java.util.HashMap<String, Integer> stack = new java.util.HashMap<>();
        for (MP.Player p : MP.players) {
            int[] v = Session.mapVotes.get(p.id);
            if (v == null) continue;
            try {
                com.megacrit.cardcrawl.map.MapRoomNode n = AbstractDungeon.map.get(v[1]).get(v[0]);
                String key = v[0] + "," + v[1];
                int k = stack.getOrDefault(key, 0);
                stack.put(key, k + 1);
                String label = p.id == MP.myId ? "You" : p.name;
                float w = FontHelper.getSmartWidth(FontHelper.tipBodyFont, label, 9999f, 0f) + 16 * Settings.scale;
                float x = n.hb.cX, y = n.hb.cY + (44 + 26 * k) * Settings.scale;
                box(sb, x - w / 2f, y - 12 * Settings.scale, w, 24 * Settings.scale, new Color(0.1f, 0.1f, 0.12f, 0.85f));
                FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, label, x, y,
                        p.id == MP.myId ? Settings.GREEN_TEXT_COLOR : Settings.GOLD_COLOR);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Screens that cover the fight (not target or orb pickers, which need the fight visible). */
    private static boolean fullScreenUp() {
        if (!AbstractDungeon.isScreenUp) return false;
        switch (AbstractDungeon.screen) {
            case MASTER_DECK_VIEW: case GAME_DECK_VIEW: case DISCARD_VIEW: case EXHAUST_VIEW: case MAP:
            case SETTINGS: case INPUT_SETTINGS: case CARD_REWARD: case GRID: case BOSS_REWARD: case COMBAT_REWARD:
            case DEATH: case VICTORY: case FTUE: case SHOP:
                return true;
            default:
                return false;
        }
    }

    private static final java.util.Random animRng = new java.util.Random();
    private static int animFace = 1;
    private static float animTick = 0f;

    /** The shared die: number, who has confirmed, and the Keep button. */
    private static void renderDie(SpriteBatch sb) {
        BoardGame.multiplayer.combat.DiePhase d = null;
        if (BoardGame.multiplayer.combat.DiePhase.roundKey < 0) return;
        int v = BoardGame.thedie.TheDie.monsterRoll;
        if (v <= 0) return;
        boolean rolling = BoardGame.multiplayer.combat.DiePhase.rollAnim > 0;
        if (rolling) {
            animTick -= Gdx.graphics.getDeltaTime();
            if (animTick <= 0) {
                animFace = animRng.nextInt(6) + 1;
                animTick = 0.07f;
            }
        }
        // compact: a small die, one status line, and the Keep button only while deciding
        boolean deciding = BoardGame.multiplayer.combat.DiePhase.deciding();
        ArrayList<String> extra = new ArrayList<>();
        ArrayList<Color> extraColor = new ArrayList<>();
        if (deciding) {
            StringBuilder who = new StringBuilder();
            for (MP.Player p : MP.players) {
                if (who.length() > 0) who.append("   ");
                who.append(p.id == MP.myId ? "You" : p.name)
                        .append(BoardGame.multiplayer.combat.DiePhase.confirmed(p.id) ? " ok" : " ...");
            }
            extra.add(who.toString());
            extraColor.add(Settings.CREAM_COLOR);
            // what this number would do, so a change can be weighed before keeping it
            for (MP.Player p : MP.players) {
                String list = BoardGame.multiplayer.combat.DiePhase.preview.get(p.id);
                if (list == null || list.isEmpty()) continue;
                extra.add("On " + v + ", " + (p.id == MP.myId ? "you" : p.name) + ": " + list);
                extraColor.add(Settings.GREEN_TEXT_COLOR);
            }
        } else {
            if (!BoardGame.multiplayer.combat.DiePhase.lastChange.isEmpty()) {
                extra.add(BoardGame.multiplayer.combat.DiePhase.lastChange);
                extraColor.add(Settings.CREAM_COLOR);
            }
            for (MP.Player p : MP.players) {
                String list = BoardGame.multiplayer.combat.DiePhase.triggered.get(p.id);
                if (list == null || list.isEmpty()) continue;
                extra.add((p.id == MP.myId ? "You" : p.name) + ": " + list);
                extraColor.add(Settings.GREEN_TEXT_COLOR);
            }
        }
        // wrap long lines (relic lists) instead of cutting them off
        ArrayList<String> wrapped = new ArrayList<>();
        ArrayList<Color> wrappedColor = new ArrayList<>();
        for (int i = 0; i < extra.size(); i++) {
            String rest = extra.get(i);
            boolean first = true;
            while (rest.length() > 46) {
                int cut = rest.lastIndexOf(' ', 46);
                if (cut < 20) cut = 46;
                wrapped.add((first ? "" : "   ") + rest.substring(0, cut).trim());
                wrappedColor.add(extraColor.get(i));
                rest = rest.substring(cut).trim();
                first = false;
            }
            wrapped.add((first ? "" : "   ") + rest);
            wrappedColor.add(extraColor.get(i));
        }
        extra = wrapped;
        extraColor = wrappedColor;
        boolean showKeep = deciding && !BoardGame.multiplayer.combat.DiePhase.iConfirmed() && !acceptRollShown();
        float lh = 22f;
        float h1080 = 56 + extra.size() * lh + (showKeep ? 44 : 0);
        dieBottom1080 = DIE_TOP + h1080;
        float x = 30 * Settings.scale, w = DIE_W * Settings.scale;
        float top = Settings.HEIGHT - DIE_TOP * Settings.scale;
        box(sb, x, top - h1080 * Settings.scale, w, h1080 * Settings.scale, BG);
        float ds = 44 * Settings.scale;
        box(sb, x + 6 * Settings.scale, top - 6 * Settings.scale - ds, ds, ds, rolling ? Color.LIGHT_GRAY : Color.WHITE);
        FontHelper.renderFontCentered(sb, FontHelper.tipHeaderFont, String.valueOf(rolling ? animFace : v),
                x + 6 * Settings.scale + ds / 2f, top - 6 * Settings.scale - ds / 2f, Color.BLACK);
        String title = deciding ? "Die " + v + ": keep it or change it" : "Die " + v + " (locked in)";
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, title, x + 60 * Settings.scale,
                top - 16 * Settings.scale, deciding ? Settings.GOLD_COLOR : Settings.GREEN_TEXT_COLOR);
        float y = top - 56 * Settings.scale;
        for (int i = 0; i < extra.size(); i++) {
            String l = extra.get(i);
            if (l.length() > 50) l = l.substring(0, 49) + "...";
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, l, x + 8 * Settings.scale, y,
                    extraColor.get(i));
            y -= lh * Settings.scale;
        }
        if (showKeep) {
            keepBtn.y = DIE_TOP + 56 + extra.size() * lh + 2;
            keepBtn.text = "Keep " + v;
            keepBtn.render(sb);
        }
    }

    /** The board game's own Accept Roll button (players with Abacus etc.) does the same thing. */
    private static boolean acceptRollShown() {
        try {
            return BoardGame.ui.OverlayMenuPatches.OverlayMenuExtraInterface.lockinrollbutton
                    .get(AbstractDungeon.overlayMenu).visible;
        } catch (Throwable t) {
            return false;
        }
    }

    private static final float DIE_TOP = 214, DIE_W = 380; // just under the History button (170 + 34)
    /** Where the die box ends (1080p units from the top); the combat log goes under it. */
    private static float dieBottom1080 = DIE_TOP;

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
        // each message on its own dark pill (readable over any screen), fading out at the end
        float s = Settings.scale;
        float y = Settings.HEIGHT - 330 * s;
        for (int i = 0; i < Session.toasts.size(); i++) {
            String t = Session.toasts.get(i);
            float life = i < Session.toastTimes.size() ? Session.toastTimes.get(i) : 1f;
            float a = Math.max(0f, Math.min(1f, life / 0.6f));
            float w = FontHelper.getSmartWidth(FontHelper.tipBodyFont, t, 9999f, 0f) + 36 * s, h = 32 * s;
            box(sb, Settings.WIDTH / 2f - w / 2f, y - h / 2f, w, h, new Color(0.04f, 0.05f, 0.08f, 0.85f * a));
            Color c = Settings.GOLD_COLOR.cpy();
            c.a = a;
            FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, t, Settings.WIDTH / 2f, y, c);
            y -= 38 * s;
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

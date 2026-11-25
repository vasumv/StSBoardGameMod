package BoardGame.multiplayer.ui;

import BoardGame.events.BGDeadAdventurer;
import BoardGame.events.BGGoldenIdolEvent;
import BoardGame.multiplayer.MP;
import BoardGame.multiplayer.Session;
import BoardGame.neow.BGNeowEvent;
import basemod.ReflectionHacks;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.RoomEventDialog;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton;

/**
 * Makes party decisions obvious: a banner says who decides, the other players' options are
 * greyed out, and Ancient Temple's option shows the extra HP it will cost you.
 */
public class PartyEventUI {
    private static String banner;
    private static String templeBase;
    private static int templeShown = -1;

    private static boolean multi() {
        return MP.inRun && MP.playerCount() > 1 && CardCrawlGame.isInARun() && AbstractDungeon.currMapNode != null
                && AbstractDungeon.getCurrRoom() != null;
    }

    private static String hostName() {
        MP.Player h = MP.player(0);
        return h == null ? "The host" : h.name;
    }

    public static void update() {
        banner = null;
        if (!multi()) return;
        AbstractEvent ev = AbstractDungeon.getCurrRoom().event;
        if (ev == null) return;
        boolean partyChoice = false;
        if (ev instanceof BGNeowEvent) {
            Integer screen = ReflectionHacks.getPrivate(ev, BGNeowEvent.class, "screenNum");
            partyChoice = screen != null && screen == 99;
            if (partyChoice) banner = MP.isHost() ? "Party decision: you choose where the party starts"
                    : "Party decision: " + hostName() + " chooses where the party starts";
        } else if (ev instanceof BGDeadAdventurer && AbstractDungeon.floorNum != 2) {
            partyChoice = true;
            banner = MP.isHost() ? "Party decision: you choose for everyone (one roll for the party)"
                    : "Party decision: " + hostName() + " chooses for the party";
        } else if (ev instanceof BGGoldenIdolEvent) {
            updateTemple((BGGoldenIdolEvent) ev);
        } else if (ev instanceof BoardGame.events.BGLab) {
            MP.Player bottom = MP.playerInRow(0);
            banner = "Lab: one die roll for the party; on 4-6 the extra potion goes to "
                    + (bottom == null ? "one player" : bottom.id == MP.myId ? "you" : bottom.name);
        } else if (ev instanceof BoardGame.events.BGBigFish) {
            banner = "Big Fish: each player must take a different option";
        }
        if (partyChoice && !MP.isHost()) {
            for (LargeDialogOptionButton b : RoomEventDialog.optionList) b.isDisabled = true;
        }
    }

    /** Ancient Temple: show what going inside costs you now. */
    private static void updateTemple(BGGoldenIdolEvent ev) {
        Integer screen = ReflectionHacks.getPrivate(ev, BGGoldenIdolEvent.class, "screenNum");
        if (screen == null || screen != 0 || ev.imageEventText.optionList.isEmpty()) {
            templeShown = -1;
            templeBase = null;
            return;
        }
        int extra = BoardGame.multiplayer.combat.SharedEvents.templeExtra();
        banner = extra == 0 ? "Ancient Temple: nobody has gone inside yet"
                : "Ancient Temple: " + extra + " player(s) went in before you: +" + extra + " HP to go inside";
        LargeDialogOptionButton b = ev.imageEventText.optionList.get(0);
        if (templeBase == null) templeBase = b.msg;
        if (extra != templeShown) {
            templeShown = extra;
            ev.imageEventText.updateDialogOption(0, extra == 0 ? templeBase : templeBase + " #r(+" + extra + " #rHP)");
        }
    }

    public static void render(SpriteBatch sb) {
        if (banner == null) return;
        float w = 900 * Settings.scale, h = 44 * Settings.scale;
        float x = Settings.WIDTH / 2f - w / 2f, y = Settings.HEIGHT - 150 * Settings.scale;
        sb.setColor(new Color(0.1f, 0.12f, 0.18f, 0.9f));
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x, y, w, h);
        sb.setColor(Color.WHITE);
        FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, banner, Settings.WIDTH / 2f, y + h / 2f,
                Settings.GOLD_COLOR);
    }
}

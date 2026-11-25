package BoardGame.multiplayer;

import BoardGame.characters.BGDefect;
import BoardGame.characters.BGIronclad;
import BoardGame.characters.BGSilent;
import BoardGame.characters.BGWatcher;
import BoardGame.multicharacter.MultiCharacter;
import BoardGame.multicharacter.MultiCharacterSelectButton;
import BoardGame.multicharacter.MultiCharacterSelectScreen;
import basemod.BaseMod;
import basemod.ReflectionHacks;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ModHelper;
import com.megacrit.cardcrawl.ui.buttons.ProceedButton;

/**
 * Starts a run for a multiplayer session: same seed for everyone, and each player's
 * lobby character is picked automatically on the mod's character-select screen.
 */
public class RunStart {
    private static final Class<?>[] CHARACTER_CLASSES = {BGIronclad.class, BGSilent.class, BGDefect.class,
            BGWatcher.class};
    private static int selectStage = -1; // -1 idle, 0 pick character, 1 press proceed, 2 done

    public static void begin() {
        BoardGame.multiplayer.ui.LobbyScreen.open = false;
        if (CardCrawlGame.mode != CardCrawlGame.GameMode.CHAR_SELECT || CardCrawlGame.mainMenuScreen == null) {
            Session.toast("Return to the main menu before starting a multiplayer run");
            MP.logger.error("cannot start run: not at the main menu");
            return;
        }
        Settings.seed = MP.seed;
        Settings.seedSet = true;
        Settings.specialSeed = null;
        Settings.isTrial = false;
        Settings.isDailyRun = false;
        Settings.isEndless = false;
        ModHelper.setModsFalse();
        AbstractDungeon.generateSeeds();
        AbstractDungeon.isAscensionMode = MP.ascension > 0;
        AbstractDungeon.ascensionLevel = MP.ascension;
        CardCrawlGame.chosenCharacter = MultiCharacter.Enums.BG_MULTICHARACTER;
        CardCrawlGame.mainMenuScreen.isFadingOut = true;
        CardCrawlGame.mainMenuScreen.fadeOutMusic();
        selectStage = 0;
    }

    public static void update() {
        if (selectStage < 0 || selectStage > 1 || !MP.inRun) return;
        if (!CardCrawlGame.isInARun() || AbstractDungeon.player == null) return;
        if (AbstractDungeon.screen != MultiCharacterSelectScreen.Enum.MULTI_CHARACTER_SELECT) return;
        if (!(AbstractDungeon.player instanceof MultiCharacter)) return;
        MultiCharacter mc = (MultiCharacter) AbstractDungeon.player;
        if (selectStage == 0) {
            MultiCharacterSelectScreen screen = (MultiCharacterSelectScreen) BaseMod.getCustomScreen(
                    MultiCharacterSelectScreen.Enum.MULTI_CHARACTER_SELECT);
            Class<?> want = CHARACTER_CLASSES[MP.me() == null ? 0 : MP.me().character];
            for (MultiCharacterSelectButton b : screen.buttons) {
                if (b.c != null && b.c.getClass() == want) {
                    b.hb.clicked = true; // the button's own update performs the selection
                    selectStage = 1;
                    return;
                }
            }
        } else if (mc.subcharacters.size() == 1) {
            Hitbox hb = ReflectionHacks.getPrivate(AbstractDungeon.overlayMenu.proceedButton, ProceedButton.class,
                    "hb");
            hb.clicked = true;
            selectStage = 2;
        }
    }
}

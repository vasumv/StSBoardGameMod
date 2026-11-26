package BoardGame.events;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.city.Colosseum;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;

//TODO: Colosseum encounters need to draw from the physical decks
// additionally, since vanilla Colosseum isn't random, this event doesn't even cycle through the monster list
public class BGColosseum extends Colosseum {    //game is hardcoded to check for instanceof Colosseum when clicking Proceed button
    public static final String ID = "BGColosseum";

    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("BoardGame:BGColosseum");

    public static final String NAME = eventStrings.NAME;

    public static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;

    public static final String[] OPTIONS = eventStrings.OPTIONS;
    public boolean isElite=false;
    public String encounterID="";

    private CurScreen screen = CurScreen.INTRO;

    private enum CurScreen {
        INTRO, FIGHT, LEAVE, POST_COMBAT;
    }

    public BGColosseum() {
        super();
    }

    // ---- multiplayer: "One choice for the party". The host clicks, every game follows, and the
    // party fights the chosen Encounter or Elite together.
    public static boolean partyDriven = false;
    private static int toastFloor = -1;

    private static boolean multi() {
        return BoardGame.multiplayer.MP.inRun && BoardGame.multiplayer.MP.playerCount() > 1;
    }

    /** A non-host game follows the host's choice. */
    public static void onPartyMessage(int from, com.google.gson.JsonObject m) {
        if (BoardGame.multiplayer.MP.isHost() || m.get("floor").getAsInt() != AbstractDungeon.floorNum) return;
        if (!(AbstractDungeon.getCurrRoom().event instanceof BGColosseum)) return;
        BGColosseum ev = (BGColosseum) AbstractDungeon.getCurrRoom().event;
        if (!ev.screen.name().equals(m.get("s").getAsString())) return; // already past it
        partyDriven = true;
        try {
            ev.buttonEffect(m.get("v").getAsInt());
        } finally {
            partyDriven = false;
        }
    }

    protected void buttonEffect(int buttonPressed) {
        if (multi() && this.screen != CurScreen.LEAVE) {
            if (!BoardGame.multiplayer.MP.isHost() && !partyDriven) {
                if (toastFloor != AbstractDungeon.floorNum) {
                    toastFloor = AbstractDungeon.floorNum;
                    BoardGame.multiplayer.Session.toast("The host chooses for the party");
                }
                return;
            }
            if (BoardGame.multiplayer.MP.isHost()) {
                com.google.gson.JsonObject o = BoardGame.multiplayer.MP.msg("colo");
                o.addProperty("s", this.screen.name());
                o.addProperty("v", buttonPressed);
                o.addProperty("floor", AbstractDungeon.floorNum);
                BoardGame.multiplayer.MP.send(o);
            }
        }
        switch (this.screen) {
            case INTRO:
                switch (buttonPressed) {
                    case 0:
                        this.imageEventText.updateBodyText(DESCRIPTIONS[1] + DESCRIPTIONS[2]);
                        this.imageEventText.updateDialogOption(0, OPTIONS[1]);
                        this.imageEventText.setDialogOption(OPTIONS[2]);
                        this.screen = CurScreen.FIGHT;
                        break;
                }
                return;
            case FIGHT:
                this.screen = CurScreen.LEAVE;
                switch (buttonPressed) {
                    case 0:
                        (AbstractDungeon.getCurrRoom()).monsters = CardCrawlGame.dungeon.getMonsterForRoomCreation();
                        encounterID=AbstractDungeon.monsterList.get(0);
                        AbstractDungeon.monsterList.remove(0);
                        (AbstractDungeon.getCurrRoom()).rewards.clear();
                        //TODO: for reasons that are still unclear, addPotionToRewards is called automatically by AbstractRoom under certain conditions at intellij line 369
                        AbstractDungeon.getCurrRoom().addGoldToRewards(0);
                        enterCombatFromImage();
                        AbstractDungeon.lastCombatMetricKey = "Standard Encounter";
                        break;
                    case 1:
                        isElite=true;
                        (AbstractDungeon.getCurrRoom()).monsters = CardCrawlGame.dungeon.getEliteMonsterForRoomCreation();
                        encounterID=AbstractDungeon.eliteMonsterList.get(0);
                        AbstractDungeon.eliteMonsterList.remove(0);
                        (AbstractDungeon.getCurrRoom()).rewards.clear();

                        (AbstractDungeon.getCurrRoom()).eliteTrigger = true;
                        AbstractDungeon.getCurrRoom().addGoldToRewards(0);
                        AbstractDungeon.getCurrRoom().addRelicToRewards(AbstractRelic.RelicTier.RARE);

                        enterCombatFromImage();
                        AbstractDungeon.lastCombatMetricKey = "Elite Encounter";
                        break;
                }
                return;
            case LEAVE:
                openMap();
                return;
        }
        openMap();
    }

    public void logMetric(String actionTaken) {
        AbstractEvent.logMetric("Colosseum", actionTaken);
    }

    public void reopen() {
        //do nothing; override Colosseum
    }
//        if (this.screen != CurScreen.LEAVE) {
//            AbstractDungeon.resetPlayer();
//            AbstractDungeon.player.drawX = Settings.WIDTH * 0.25F;
//            AbstractDungeon.player.preBattlePrep();
//            enterImageFromCombat();
//            this.imageEventText.updateBodyText(DESCRIPTIONS[4]);
//            this.imageEventText.updateDialogOption(0, OPTIONS[2]);
//            this.imageEventText.setDialogOption(OPTIONS[3]);
//        }
//    }
}


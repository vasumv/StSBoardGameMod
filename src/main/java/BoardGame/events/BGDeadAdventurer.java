package BoardGame.events;

import BoardGame.dungeons.BGExordium;
import BoardGame.potions.BGGamblersBrew;
import BoardGame.relics.BGSsserpentHead;
import basemod.ReflectionHacks;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.RoomEventDialog;
import com.megacrit.cardcrawl.events.exordium.DeadAdventurer;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

//TODO: consider using discarded relics for "drew encounter or merchant on floor 2"
public class BGDeadAdventurer
        extends DeadAdventurer {   //game is hardcoded to check for DeadAdventurer when completing event

    private static final Logger logger = LogManager.getLogger(DeadAdventurer.class.getName());

    public static final String ID = "BGDeadAdventurer";

    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("BoardGame:BGDeadAdventurer");

    public static final String NAME = eventStrings.NAME;

    public static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;

    public static final String[] OPTIONS = eventStrings.OPTIONS;

    private static final String FIGHT_MSG = DESCRIPTIONS[0];

    private static final String ESCAPE_MSG = DESCRIPTIONS[1];

    private int numRewards = 0;

    private int encounterChance = 0;

    private ArrayList<String> rewards = new ArrayList<>();

    private float x = 800.0F * Settings.xScale;

    private float y = AbstractDungeon.floorY;

    private int enemy = 0;

    private CUR_SCREEN screen = CUR_SCREEN.INTRO;

    private static final Color DARKEN_COLOR = new Color(0.5F, 0.5F, 0.5F, 1.0F);

    private Texture adventurerImg;

    private int goldRewardMetric = 0;

    private int leaveIndex,takePrizeIndex,gamblingChipIndex, theAbacusIndex,toolboxIndex,potionIndex;
    public boolean alreadyUsedGamblingChip,alreadyUsedTheAbacus,alreadyUsedToolbox;
    public boolean encounterHasBeenSpawned=false;
    private AbstractRelic relicRewardMetric = null;

    public String encounterID;
    public int pendingReward =0;

    private enum CUR_SCREEN {
        INTRO, FAIL, SUCCESS, ESCAPE, POTION;
    }

    public BGDeadAdventurer() {
        potionIndex=theAbacusIndex=toolboxIndex=gamblingChipIndex=-1;
        pendingReward=0;
        takePrizeIndex=0;leaveIndex=1;
        this.enemy = AbstractDungeon.miscRng.random(0, 2);
        this.adventurerImg = ImageMaster.loadImage("images/npcs/nopants.png");
        this.body = DESCRIPTIONS[2];
        this.roomEventText.clear();
        if(AbstractDungeon.floorNum==2 && CardCrawlGame.dungeon instanceof BGExordium){
            this.body += DESCRIPTIONS[3];
            this.roomEventText.addDialogOption(OPTIONS[5]);
        }else {
            this.body += DESCRIPTIONS[4];
            this.body += DESCRIPTIONS[6];
            this.roomEventText.addDialogOption(OPTIONS[0]);
            this.roomEventText.addDialogOption(OPTIONS[1]);
        }
        this.hasDialog = true;
        this.hasFocus = true;
    }

    public void update() {
        super.update();
        if ((AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.EVENT ||
                (AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMPLETE) {
            this.imgColor = Color.WHITE.cpy();
        } else {
            this.imgColor = DARKEN_COLOR;
        }
        if (!RoomEventDialog.waitForInput)
            buttonEffect(this.roomEventText.getSelectedOption());
    }
//TODO: if die relics are used here, they shouldn't be available for the elite fight
    protected void buttonEffect(int buttonPressed) {
        if(AbstractDungeon.floorNum==2 && CardCrawlGame.dungeon instanceof BGExordium){
            //make sure ssserpenthead happens only ONCE
            if(AbstractDungeon.player.hasRelic("BGSsserpentHead")){
                AbstractDungeon.player.loseGold(BGSsserpentHead.GOLD_AMT);
            }
            AbstractDungeon.nextRoom=AbstractDungeon.getCurrMapNode();
            AbstractDungeon.nextRoom.room=AbstractDungeon.getCurrRoom();
            ReflectionHacks.setPrivateStatic(AbstractDungeon.class,"fadeTimer",0F);
            AbstractDungeon.nextRoomTransitionStart();
            //TODO LATER: this will probably break something if the player S&Qs immediately
            AbstractDungeon.floorNum-=1;
            return;
        }
        // multiplayer: one choice for the party; the host decides and every game follows
        if (BoardGame.multiplayer.MP.inRun && BoardGame.multiplayer.MP.playerCount() > 1) {
            if (!BoardGame.multiplayer.MP.isHost() && !partyDriven) {
                if (buttonPressed >= 0 && toastFloor != AbstractDungeon.floorNum) {
                    toastFloor = AbstractDungeon.floorNum;
                    BoardGame.multiplayer.Session.toast("The host chooses for the party");
                }
                return;
            }
            if (BoardGame.multiplayer.MP.isHost()) {
                if (this.screen == CUR_SCREEN.INTRO && buttonPressed == takePrizeIndex && pendingReward <= 0)
                    partySend("search", 0);
                else if (this.screen == CUR_SCREEN.INTRO && buttonPressed == leaveIndex) partySend("leave", 0);
                else if (this.screen == CUR_SCREEN.FAIL || this.screen == CUR_SCREEN.SUCCESS
                        || this.screen == CUR_SCREEN.ESCAPE) partySend("continue", 0);
            }
        }
        switch (this.screen) {
            case INTRO:
                if(buttonPressed==takePrizeIndex) {
                    if(pendingReward<=0) {
                        roomEventText.clear();
                        pendingReward = AbstractDungeon.miscRng.random(1, 6);
                        if (BoardGame.multiplayer.ui.EventDie.multi()) {
                            // one die for the party: anyone may change it, then it resolves everywhere
                            this.roomEventText.updateBodyText("The party searches the body...");
                            BoardGame.multiplayer.ui.EventDie.startParty(
                                    "Dead Adventurer: one search for the party", pendingReward, v -> {
                                        pendingReward = v;
                                        describeRoll();
                                        applyingResult = true;
                                        try {
                                            takeReward(false);
                                        } finally {
                                            applyingResult = false;
                                        }
                                    });
                            return;
                        }
                        describeRoll();
                        setupButtons();
                    }else{
                        //if reward isn't 0, then we've already rolled and we've decided not to use any die items
                        takeReward(true);
                    }
                }else if(buttonPressed==leaveIndex){
                    this.screen = CUR_SCREEN.ESCAPE;
                    this.roomEventText.updateBodyText(ESCAPE_MSG);
                    this.roomEventText.updateDialogOption(0, OPTIONS[1]);
                    this.roomEventText.removeDialogOption(1);
                }else if(buttonPressed==gamblingChipIndex){
                    alreadyUsedGamblingChip=true;
                    pendingReward=0;
                    buttonEffect(takePrizeIndex);
                }else if(buttonPressed==theAbacusIndex){
                    alreadyUsedTheAbacus=true;
                    pendingReward+=1;if(pendingReward>6)pendingReward=1;
                    takeReward(true);
                }else if(buttonPressed==toolboxIndex){
                    alreadyUsedToolbox=true;
                    pendingReward-=1;if(pendingReward<1)pendingReward=6;
                    takeReward(true);
                }else if(buttonPressed==potionIndex){
                    int slot = BGGamblersBrew.doesPlayerHaveGamblersBrew();
                    if (slot>-1) {
                        AbstractDungeon.topPanel.destroyPotion(slot);
                        //TODO: is there a clearOptionsOnlyAndLeaveDescriptionPreserved function?
                        this.roomEventText.clear();
                        this.roomEventText.addDialogOption(OPTIONS[10]);
                        this.roomEventText.addDialogOption(OPTIONS[11]);
                        this.roomEventText.addDialogOption(OPTIONS[12]);
                        this.screen = CUR_SCREEN.POTION;
                        return;
                    }
                }
                return;
            case SUCCESS:
                openMap();
                return;
            case FAIL:
                beginEliteEncounter();
                return;
            case ESCAPE:
                AbstractEvent.logMetric("Dead Adventurer","Escaped");
                openMap();
                return;
            case POTION:
                if(buttonPressed==0)pendingReward=1;
                else if(buttonPressed==1)pendingReward=3;
                else if(buttonPressed==2)pendingReward=5;
                takeReward(true);
                return;
        }
        logger.info("WHY YOU CALLED?");
    }

    private void describeRoll() {
        if (pendingReward == 1 || pendingReward == 2) {
            this.roomEventText.updateBodyText(FIGHT_MSG);
            roomEventText.addDialogOption(OPTIONS[2]);
        } else if (pendingReward == 3 || pendingReward == 4) {
            this.roomEventText.updateBodyText(DESCRIPTIONS[7] + DESCRIPTIONS[10]);
            roomEventText.addDialogOption(OPTIONS[1]);
        } else if ((pendingReward == 5 || pendingReward == 6) && BoardGame.multiplayer.ui.EventDie.multi()) {
            this.roomEventText.updateBodyText("The party finds a #yrelic among his belongings. The host chooses who takes it.");
            roomEventText.addDialogOption(OPTIONS[1]);
        } else if (pendingReward == 5 || pendingReward == 6) {
            this.roomEventText.updateBodyText(DESCRIPTIONS[9] + DESCRIPTIONS[10]);
            roomEventText.addDialogOption(OPTIONS[1]);
        }
    }

    protected void setupButtons(){
        leaveIndex=potionIndex=theAbacusIndex=toolboxIndex=gamblingChipIndex=-1;
        int i = 1;
        if(pendingReward>0) {
            int pendingRewardTextIndex=(pendingReward+1)/2;
            if (AbstractDungeon.player.hasRelic("BGGambling Chip")) {
                if (!alreadyUsedGamblingChip) {
                    //REMINDER: this uses roomEventText instead of imageEventText
                    this.roomEventText.addDialogOption("[Gambling Chip] Reroll.");
                    gamblingChipIndex = i;
                    i += 1;
                }
            }
            if (AbstractDungeon.player.hasRelic("BGTheAbacus")) {
                if(!alreadyUsedTheAbacus) {
                    int r2 = pendingReward + 1;
                    if (r2 > 6) r2 = 1;
                    if (r2 == 1 || r2 == 3 || r2 == 5) {  //only show option if new reward is different
                        this.roomEventText.addDialogOption("[The Abacus] " + OPTIONS[(r2 + 1) / 2 + 9]);
                        theAbacusIndex = i;
                        i += 1;
                    }
                }
            }
            if (AbstractDungeon.player.hasRelic("BGToolbox")) {
                if(!alreadyUsedToolbox) {
                    int r2 = pendingReward - 1;
                    if (r2 < 1) r2 = 6;
                    if (r2 == 2 || r2 == 4 || r2 == 6) {
                        this.roomEventText.addDialogOption("[Toolbox] " + OPTIONS[(r2 + 1) / 2 + 9]);
                        toolboxIndex = i;
                        i += 1;
                    }
                }
            }
            if (BGGamblersBrew.doesPlayerHaveGamblersBrew() > -1) {
                this.roomEventText.addDialogOption("[Gambler's Brew] Choose a reward.");
                potionIndex = i;
                i += 1;
            }
            if(i==1){
                //no items remaining, force reward
                takeReward(false);
            }else{
                if(pendingReward==1 || pendingReward==2){
                    this.roomEventText.updateBodyText(DESCRIPTIONS[11]);
                }
            }
        }
        int x=0;
        x+=1;
    }


//    public void logMetric(int numAttempts) {
//        if (this.relicRewardMetric != null) {
//            AbstractEvent.logMetricGainGoldAndRelic("Dead Adventurer", "Searched '" + numAttempts + "' times", this.relicRewardMetric, this.goldRewardMetric);
//        } else {
//            AbstractEvent.logMetricGainGold("Dead Adventurer", "Searched '" + numAttempts + "' times", this.goldRewardMetric);
//        }
//    }

    public void render(SpriteBatch sb) {
        super.render(sb);
        sb.setColor(Color.WHITE);
        sb.draw(this.adventurerImg, this.x - 146.0F, this.y - 86.5F, 146.0F, 86.5F, 292.0F, 173.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 292, 173, false, false);
    }


    public void spawnEliteEncounter(){
        encounterHasBeenSpawned=true;
        (AbstractDungeon.getCurrRoom()).monsters = CardCrawlGame.dungeon.getEliteMonsterForRoomCreation();
        encounterID=AbstractDungeon.eliteMonsterList.get(0);
        AbstractDungeon.eliteMonsterList.remove(0);
        (AbstractDungeon.getCurrRoom()).eliteTrigger = true;
    }
    public void beginEliteEncounter(){
        if(!encounterHasBeenSpawned)spawnEliteEncounter();
        //thankfully, BG act1 elites are ALWAYS card+2gold+relic, so we don't need to check the rewards table
        AbstractDungeon.getCurrRoom().addGoldToRewards(2);
        AbstractDungeon.getCurrRoom().addRelicToRewards(AbstractDungeon.returnRandomRelicTier());
        enterCombat();
        AbstractDungeon.lastCombatMetricKey = encounterID;
        this.numRewards++;
        logMetric(this.numRewards);
    }

    // ---- multiplayer party decision ----
    public static boolean partyDriven = false;
    private static int toastFloor = -1;

    private static void partySend(String action, int value) {
        com.google.gson.JsonObject o = BoardGame.multiplayer.MP.msg("deadadv");
        o.addProperty("a", action);
        o.addProperty("v", value);
        o.addProperty("floor", AbstractDungeon.floorNum);
        BoardGame.multiplayer.MP.send(o);
    }

    /** A non-host game follows the host's decision. */
    public static void onPartyMessage(int from, com.google.gson.JsonObject m) {
        if (m.get("floor").getAsInt() != AbstractDungeon.floorNum) return;
        if ("relicto".equals(m.get("a").getAsString())) { // every game, the host's too
            if (m.get("v").getAsInt() == BoardGame.multiplayer.MP.myId) {
                AbstractRelic r = AbstractDungeon.returnRandomScreenlessRelic(AbstractDungeon.returnRandomRelicTier());
                AbstractDungeon.getCurrRoom().spawnRelicAndObtain(Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F, r);
                AbstractEvent.logMetricGainGoldAndRelic("Dead Adventurer", "Gained Relic", r, 0);
            } else {
                BoardGame.multiplayer.Session.toast(BoardGame.multiplayer.Session.nameOf(m.get("v").getAsInt())
                        + " gets the Dead Adventurer's Relic");
            }
            return;
        }
        if (BoardGame.multiplayer.MP.isHost()) return;
        if (!(AbstractDungeon.getCurrRoom().event instanceof BGDeadAdventurer)) return;
        BGDeadAdventurer ev = (BGDeadAdventurer) AbstractDungeon.getCurrRoom().event;
        partyDriven = true;
        try {
            switch (m.get("a").getAsString()) {
                case "search":
                    ev.pendingReward = 0; // the host rolled (or rerolled): roll the same shared number
                    ev.buttonEffect(ev.takePrizeIndex);
                    break;
                case "leave": ev.buttonEffect(ev.leaveIndex); break;
                case "result":
                    ev.pendingReward = m.get("v").getAsInt();
                    applyingResult = true;
                    try {
                        ev.takeReward(true);
                    } finally {
                        applyingResult = false;
                    }
                    break;
                case "continue": ev.buttonEffect(0); break;
            }
        } finally {
            partyDriven = false;
        }
    }

    private static boolean applyingResult = false;

    public void takeReward(boolean skipContinueButton){
        // multiplayer: only the party die's final number counts, applied in every game at once
        if (BoardGame.multiplayer.MP.inRun && BoardGame.multiplayer.MP.playerCount() > 1 && !applyingResult) return;
        if(pendingReward==1 || pendingReward==2){
            spawnEliteEncounter();
            screen= CUR_SCREEN.FAIL;
            if(skipContinueButton) beginEliteEncounter();
        }else if(pendingReward==3 || pendingReward==4){
            AbstractDungeon.player.gainGold(2);
            AbstractEvent.logMetricGainGold("Dead Adventurer", "Gained Gold", this.goldRewardMetric);
            screen=CUR_SCREEN.SUCCESS;
            if(skipContinueButton) openMap();
        }else if((pendingReward==5 || pendingReward==6) && BoardGame.multiplayer.MP.inRun
                && BoardGame.multiplayer.MP.playerCount() > 1){
            // board game: ONE player gains the Relic; the host (who decides for the party) picks who
            screen=CUR_SCREEN.SUCCESS;
            if (BoardGame.multiplayer.MP.isHost()) {
                java.util.ArrayList<String> labels = new java.util.ArrayList<>();
                java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
                for (BoardGame.multiplayer.MP.Player p : BoardGame.multiplayer.MP.players) {
                    if (!p.connected || p.dead) continue;
                    labels.add(p.id == BoardGame.multiplayer.MP.myId ? "You" : p.name);
                    ids.add(p.id);
                }
                BoardGame.multiplayer.ui.ChoicePrompt.ask("Dead Adventurer: who gets the Relic?", labels,
                        i -> partySend("relicto", ids.get(i)));
            }
        }else if(pendingReward==5 || pendingReward==6){
            AbstractRelic r = AbstractDungeon.returnRandomScreenlessRelic(
                    AbstractDungeon.returnRandomRelicTier());
            AbstractDungeon.getCurrRoom().spawnRelicAndObtain(Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F, r);
            AbstractEvent.logMetricGainGoldAndRelic("Dead Adventurer", "Gained Relic", r,0);
            screen=CUR_SCREEN.SUCCESS;
            if(skipContinueButton) openMap();
        }
    }

    public void dispose() {
        super.dispose();
        if (this.adventurerImg != null) {
            this.adventurerImg.dispose();
            this.adventurerImg = null;
        }
    }
}


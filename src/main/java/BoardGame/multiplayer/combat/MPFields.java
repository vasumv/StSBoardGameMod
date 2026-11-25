package BoardGame.multiplayer.combat;

import com.evacipated.cardcrawl.modthespire.lib.SpireField;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class MPFields {
    @SpirePatch(clz = AbstractMonster.class, method = SpirePatch.CLASS)
    public static class Monster {
        /** Stable id shared by every game in the session. */
        public static SpireField<String> id = new SpireField<>(() -> null);
        /** Screen offset applied for row layout; base position = draw position minus this. */
        public static SpireField<float[]> layout = new SpireField<>(() -> new float[]{0f, 0f});
    }

    @SpirePatch(clz = AbstractGameAction.class, method = SpirePatch.CLASS)
    public static class Action {
        /** The monster whose turn created this action (directly or through a chain), if any. */
        public static SpireField<AbstractMonster> sourceMonster = new SpireField<>(() -> null);
        /** The move (nextMove) the monster was making when it created this action. */
        public static SpireField<Integer> sourceMove = new SpireField<>(() -> -1);
        /** Order of this action among its monster's player-targeted actions this turn (-1: not counted yet). */
        public static SpireField<Integer> hitIndex = new SpireField<>(() -> -1);
        /** The card whose play created this action, if any. */
        public static SpireField<com.megacrit.cardcrawl.cards.AbstractCard> sourceCard = new SpireField<>(() -> null);
    }
}

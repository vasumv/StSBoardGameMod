package BoardGame.monsters;

/**
 * Board game rule: enemies act row by row (top row first), left to right; enemies whose card or
 * current move says "Acts last" act after all other non-boss enemies; Bosses always act last.
 * Implemented by monsters with an "Acts last" move. See {@link BoardGame.patches.MonsterActOrderPatch}.
 */
public interface ActsLast {
    /** True when this monster's current intent (nextMove) acts last. */
    boolean actsLast();
}

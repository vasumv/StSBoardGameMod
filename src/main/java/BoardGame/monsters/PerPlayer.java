package BoardGame.monsters;

import BoardGame.multiplayer.MP;

/** Enemy values printed "per player" on the board game cards. Solo = 1 player. */
public class PerPlayer {
    public static int count() {
        return MP.inRun ? Math.max(1, MP.playerCount()) : 1;
    }

    /** amount * number of players. */
    public static int scale(int amount) {
        return amount * count();
    }
}

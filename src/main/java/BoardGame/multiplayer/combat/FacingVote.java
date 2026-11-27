package BoardGame.multiplayer.combat;

import BoardGame.multiplayer.MP;
import BoardGame.multiplayer.Session;
import BoardGame.multiplayer.ui.ChoicePrompt;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.actions.GameActionManager;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Spire Shield / Spire Spear (Act 4): "After start of turn effects, players can switch rows. Then
 * they're immediately targeted by the Facing ability from the enemy in their row."
 * <p>
 * With 3-4 players the rows matter, so each turn everyone is asked whether to stay or switch with
 * a teammate. Two players who pick each other swap rows. Once everyone has answered, each game
 * applies Facing to its own player from the enemy covering their (new) row. With 2 players both
 * enemies cover both rows, so nobody is asked.
 */
public class FacingVote {
    private static int turn = -1;
    private static boolean asked = false, resolved = false;
    private static java.util.function.IntConsumer prompt; // our open question, if any
    private static final HashMap<Integer, Integer> choice = new HashMap<>(); // player -> teammate id, or -1 to stay

    /** Called each frame by the waiting Facing action: true once Facing can be applied. */
    public static boolean ready(String myFacing, java.util.function.IntFunction<String> facingForRow) {
        int n = MP.playerCount();
        if (n < 3) return true;
        int t = com.megacrit.cardcrawl.dungeons.AbstractDungeon.floorNum * 1000 + GameActionManager.turn; // unique per fight and turn
        if (t != turn) {
            turn = t;
            asked = false;
            resolved = false;
            choice.keySet().removeIf(k -> true);
        }
        if (resolved) return true;
        if (!asked) {
            asked = true;
            ArrayList<String> labels = new ArrayList<>();
            ArrayList<Integer> ids = new ArrayList<>();
            labels.add("Stay in row " + (MP.myRow() + 1) + ": " + myFacing);
            ids.add(-1);
            for (MP.Player p : MP.players) {
                if (p.id == MP.myId || !p.connected || p.dead) continue;
                labels.add("Switch with " + p.name + " (row " + (p.row + 1) + "): " + facingForRow.apply(p.row));
                ids.add(p.id);
            }
            ChoicePrompt.ask("Spire Shield and Spear: switch rows before Facing? (both must pick each other)",
                    labels, prompt = i -> {
                        JsonObject o = MP.msg("facing");
                        o.addProperty("turn", turn);
                        o.addProperty("with", ids.get(Math.max(0, Math.min(ids.size() - 1, i))));
                        MP.send(o);
                    });
        }
        return false;
    }

    /** The fight ended (e.g. the last enemy died during the question): drop the question. */
    public static void fightOver() {
        if (prompt != null) {
            ChoicePrompt.close(prompt);
            prompt = null;
        }
    }

    public static void onMessage(int from, JsonObject m) {
        int mt = m.get("turn").getAsInt();
        if (turn != -1 && mt < turn) return; // an old turn
        if (mt != turn) { // ahead of us: start tracking that turn
            turn = mt;
            asked = false;
            resolved = false;
            choice.clear();
        }
        choice.put(from, m.get("with").getAsInt());
        for (MP.Player p : MP.players) if (p.connected && !p.dead && !choice.containsKey(p.id)) return;
        // everyone answered: swap the pairs who picked each other (same result in every game)
        for (MP.Player a : MP.players) {
            Integer w = choice.get(a.id);
            if (w == null || w < 0 || a.id > w) continue;
            Integer back = choice.get(w);
            MP.Player b = MP.player(w);
            if (back == null || back != a.id || b == null) continue;
            int r = a.row;
            a.row = b.row;
            b.row = r;
            Session.toast(a.name + " and " + b.name + " switched rows");
        }
        resolved = true;
    }
}

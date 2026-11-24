package BoardGame.multiplayer.combat;

import com.badlogic.gdx.math.RandomXS128;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.random.Random;

import java.lang.reflect.Field;

/** Copies the dungeon random generators between games so shared outcomes stay identical. */
public class RngSync {
    public static final String[] COMBAT = {"aiRng", "monsterRng", "monsterHpRng"};
    public static final String[] ALL = {"monsterRng", "mapRng", "eventRng", "merchantRng", "cardRng", "treasureRng",
            "relicRng", "potionRng", "monsterHpRng", "aiRng", "shuffleRng", "cardRandomRng", "miscRng"};

    private static Random get(String name) {
        try {
            Field f = AbstractDungeon.class.getField(name);
            return (Random) f.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    public static JsonObject capture(String[] names) {
        JsonObject o = new JsonObject();
        for (String n : names) {
            Random r = get(n);
            if (r == null) continue;
            JsonArray a = new JsonArray();
            a.add(new JsonPrimitive(r.counter));
            a.add(new JsonPrimitive(r.random.getState(0)));
            a.add(new JsonPrimitive(r.random.getState(1)));
            o.add(n, a);
        }
        return o;
    }

    public static void apply(JsonObject o) {
        if (o == null) return;
        for (String n : ALL) {
            if (!o.has(n)) continue;
            Random r = get(n);
            if (r == null) continue;
            JsonArray a = o.getAsJsonArray(n);
            r.counter = a.get(0).getAsInt();
            r.random.setState(a.get(1).getAsLong(), a.get(2).getAsLong());
        }
    }

    public static long[][] save(Random[] rs) {
        long[][] s = new long[rs.length][];
        for (int i = 0; i < rs.length; i++) {
            if (rs[i] == null) continue;
            s[i] = new long[]{rs[i].counter, rs[i].random.getState(0), rs[i].random.getState(1)};
        }
        return s;
    }

    public static void restore(Random[] rs, long[][] s) {
        for (int i = 0; i < rs.length; i++) {
            if (rs[i] == null || s[i] == null) continue;
            rs[i].counter = (int) s[i][0];
            rs[i].random.setState(s[i][1], s[i][2]);
        }
    }
}

package BoardGame.multiplayer.combat;

import BoardGame.multicharacter.MultiCreature;
import BoardGame.multiplayer.MP;
import com.badlogic.gdx.graphics.Color;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.EnemyMoveInfo;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoomBoss;
import com.megacrit.cardcrawl.vfx.combat.DamageNumberEffect;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;
import com.megacrit.cardcrawl.actions.AbstractGameAction;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Serialises the enemy side of combat so it can be kept identical across games.
 *
 * Full state (after a queued play) overwrites; deltas (simultaneous effects such as
 * end-of-turn orbs, potions, start-of-turn relics) are additive for hit points, block
 * and power amounts, and last-writer-wins for everything else.
 */
public class MonsterSync {
    private static final String[] HANDLED = {"currentHealth", "maxHealth", "currentBlock", "isDead", "isDying",
            "halfDead", "escaped", "id", "name", "isPlayer", "powers", "move", "damage"};
    private static final HashSet<String> HANDLED_SET = new HashSet<>(java.util.Arrays.asList(HANDLED));

    // ---------- ids, rows, layout ----------

    public static int row(AbstractMonster m) {
        Integer r = MultiCreature.Field.currentRow.get(m);
        return r == null ? 0 : r;
    }

    public static String id(AbstractMonster m) {
        return MPFields.Monster.id.get(m);
    }

    private static int nextSpawn = 0;
    /** The room whose fight is shared by the whole party (null when the local fight is solo). */
    public static AbstractRoom sharedRoom;

    public static void resetIds() {
        nextSpawn = 0;
    }

    public static String nextId() {
        return "m" + (nextSpawn++);
    }

    /** Give ids to monsters that lack one, in list order (identical in every game). */
    public static void assignIds() {
        MonsterGroup g = group();
        if (g == null) return;
        HashSet<String> seen = new HashSet<>();
        for (AbstractMonster m : g.monsters) {
            // summons can be pooled objects that still carry an id from an earlier fight
            if (id(m) == null || !seen.add(id(m))) {
                MPFields.Monster.id.set(m, "m" + (nextSpawn++));
                seen.add(id(m));
            }
        }
    }

    public static MonsterGroup group() {
        if (AbstractDungeon.currMapNode == null || AbstractDungeon.getCurrRoom() == null) return null;
        return AbstractDungeon.getCurrRoom().monsters;
    }

    public static AbstractMonster byId(String id) {
        MonsterGroup g = group();
        if (g == null || id == null) return null;
        for (AbstractMonster m : g.monsters) if (id.equals(id(m))) return m;
        return null;
    }

    public static boolean isBossRoom() {
        return AbstractDungeon.getCurrRoom() instanceof MonsterRoomBoss;
    }

    /** Does this enemy act against the local player? (Its row, or a Boss.) */
    public static boolean targetsMe(AbstractMonster m) {
        if (!MP.inRun || MP.playerCount() < 2 || !active()) return true;
        if (isBossRoom()) return true;
        return row(m) == MP.myRow();
    }

    /** Screen offset for a row other than the local player's. */
    public static float[] layoutFor(int row) {
        if (!MP.inRun || MP.playerCount() < 2 || isBossRoom() || row == MP.myRow()) return new float[]{0f, 0f};
        ArrayList<Integer> others = new ArrayList<>();
        for (MP.Player p : MP.players) if (p.row != MP.myRow()) others.add(p.row);
        if (!others.contains(row)) others.add(row);
        java.util.Collections.sort(others);
        int k = others.indexOf(row) + 1;
        float s = com.megacrit.cardcrawl.core.Settings.scale;
        return new float[]{-90f * k * s, 250f * k * s};
    }

    public static void applyLayout(AbstractMonster m) {
        float[] want = layoutFor(row(m));
        float[] have = MPFields.Monster.layout.get(m);
        m.drawX += want[0] - have[0];
        m.drawY += want[1] - have[1];
        MPFields.Monster.layout.set(m, want);
        refreshHitbox(m);
    }

    private static Method refreshHb;

    private static void refreshHitbox(AbstractCreature c) {
        try {
            if (refreshHb == null) {
                refreshHb = AbstractCreature.class.getDeclaredMethod("refreshHitboxLocation");
                refreshHb.setAccessible(true);
            }
            refreshHb.invoke(c);
        } catch (Throwable ignored) {
        }
    }

    // ---------- capture ----------

    public static JsonObject capture() {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        MonsterGroup g = group();
        if (g != null) {
            assignIds();
            for (AbstractMonster m : g.monsters) arr.add(captureMonster(m));
        }
        root.add("monsters", arr);
        root.addProperty("next", nextSpawn);
        return root;
    }

    private static JsonObject captureMonster(AbstractMonster m) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id(m));
        o.addProperty("cls", m.getClass().getName());
        o.addProperty("row", row(m));
        float[] lay = MPFields.Monster.layout.get(m);
        o.addProperty("bx", m.drawX - lay[0]);
        o.addProperty("by", m.drawY - lay[1]);
        o.addProperty("hp", m.currentHealth);
        o.addProperty("maxHp", m.maxHealth);
        o.addProperty("block", m.currentBlock);
        o.addProperty("dead", m.isDead || m.isDying);
        o.addProperty("escaped", m.escaped);
        o.addProperty("halfDead", m.halfDead);
        o.add("f", captureFields(m, AbstractCreature.class));
        EnemyMoveInfo mv = getMove(m);
        if (mv != null) {
            JsonObject mo = new JsonObject();
            mo.addProperty("n", mv.nextMove);
            mo.addProperty("i", mv.intent == null ? "" : mv.intent.name());
            mo.addProperty("d", mv.baseDamage);
            mo.addProperty("x", mv.multiplier);
            mo.addProperty("md", mv.isMultiDamage);
            o.add("move", mo);
        }
        JsonArray dmg = new JsonArray();
        for (DamageInfo d : m.damage) {
            JsonObject dj = new JsonObject();
            dj.addProperty("b", d.base);
            dj.addProperty("o", d.output);
            dj.addProperty("t", d.type.name());
            dmg.add(dj);
        }
        o.add("damage", dmg);
        JsonArray pw = new JsonArray();
        for (AbstractPower p : m.powers) {
            JsonObject pj = new JsonObject();
            pj.addProperty("cls", p.getClass().getName());
            pj.addProperty("pid", p.ID);
            pj.addProperty("amount", p.amount);
            pj.add("f", captureFields(p, AbstractPower.class));
            pw.add(pj);
        }
        o.add("powers", pw);
        return o;
    }

    private static final HashMap<Class<?>, List<Field>> FIELD_CACHE = new HashMap<>();

    /** Simple-valued instance fields from c up to and including stop. */
    private static List<Field> simpleFields(Class<?> c, Class<?> stop) {
        List<Field> l = FIELD_CACHE.get(c);
        if (l != null) return l;
        l = new ArrayList<>();
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isFinal(mod) || Modifier.isTransient(mod)) continue;
                if (HANDLED_SET.contains(f.getName())) continue;
                Class<?> t = f.getType();
                boolean ok = t == int.class || t == boolean.class || t == byte.class || t == short.class
                        || t == long.class || t == char.class || t == String.class || t.isEnum()
                        || t == int[].class || t == boolean[].class || t == byte[].class;
                if (!ok) continue;
                f.setAccessible(true);
                l.add(f);
            }
            if (k == stop) break;
        }
        FIELD_CACHE.put(c, l);
        return l;
    }

    private static JsonObject captureFields(Object o, Class<?> stop) {
        JsonObject f = new JsonObject();
        for (Field fd : simpleFields(o.getClass(), stop)) {
            try {
                Object v = fd.get(o);
                String key = fd.getDeclaringClass().getSimpleName() + "." + fd.getName();
                if (v == null) continue;
                if (v instanceof Boolean) f.addProperty(key, (Boolean) v);
                else if (v instanceof Number) f.addProperty(key, (Number) v);
                else if (v instanceof Character) f.addProperty(key, (Character) v);
                else if (v instanceof String) f.addProperty(key, (String) v);
                else if (v instanceof Enum) f.addProperty(key, ((Enum<?>) v).name());
                else if (v instanceof int[]) {
                    JsonArray a = new JsonArray();
                    for (int x : (int[]) v) a.add(new JsonPrimitive(x));
                    f.add(key, a);
                } else if (v instanceof boolean[]) {
                    JsonArray a = new JsonArray();
                    for (boolean x : (boolean[]) v) a.add(new JsonPrimitive(x));
                    f.add(key, a);
                } else if (v instanceof byte[]) {
                    JsonArray a = new JsonArray();
                    for (byte x : (byte[]) v) a.add(new JsonPrimitive(x));
                    f.add(key, a);
                }
            } catch (Throwable ignored) {
            }
        }
        return f;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void applyFields(Object o, Class<?> stop, JsonObject f) {
        if (f == null) return;
        for (Field fd : simpleFields(o.getClass(), stop)) {
            String key = fd.getDeclaringClass().getSimpleName() + "." + fd.getName();
            if (!f.has(key)) continue;
            JsonElement e = f.get(key);
            try {
                Class<?> t = fd.getType();
                if (t == int.class) fd.setInt(o, e.getAsInt());
                else if (t == boolean.class) fd.setBoolean(o, e.getAsBoolean());
                else if (t == byte.class) fd.setByte(o, e.getAsByte());
                else if (t == short.class) fd.setShort(o, e.getAsShort());
                else if (t == long.class) fd.setLong(o, e.getAsLong());
                else if (t == char.class) fd.setChar(o, e.getAsCharacter());
                else if (t == String.class) fd.set(o, e.getAsString());
                else if (t.isEnum()) fd.set(o, Enum.valueOf((Class) t, e.getAsString()));
                else if (t == int[].class) {
                    JsonArray a = e.getAsJsonArray();
                    int[] v = new int[a.size()];
                    for (int i = 0; i < v.length; i++) v[i] = a.get(i).getAsInt();
                    fd.set(o, v);
                } else if (t == boolean[].class) {
                    JsonArray a = e.getAsJsonArray();
                    boolean[] v = new boolean[a.size()];
                    for (int i = 0; i < v.length; i++) v[i] = a.get(i).getAsBoolean();
                    fd.set(o, v);
                } else if (t == byte[].class) {
                    JsonArray a = e.getAsJsonArray();
                    byte[] v = new byte[a.size()];
                    for (int i = 0; i < v.length; i++) v[i] = a.get(i).getAsByte();
                    fd.set(o, v);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static Field moveField;

    private static EnemyMoveInfo getMove(AbstractMonster m) {
        try {
            if (moveField == null) {
                moveField = AbstractMonster.class.getDeclaredField("move");
                moveField.setAccessible(true);
            }
            return (EnemyMoveInfo) moveField.get(m);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void setMove(AbstractMonster m, EnemyMoveInfo mv) {
        try {
            getMove(m);
            moveField.set(m, mv);
        } catch (Throwable ignored) {
        }
    }

    // ---------- apply full state ----------

    /** Overwrite the local enemies with a full captured state. */
    public static void applyFull(JsonObject root, boolean showEffects) {
        MonsterGroup g = group();
        if (g == null || root == null) return;
        assignIds();
        JsonArray arr = root.getAsJsonArray("monsters");
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            AbstractMonster m = byId(o.get("id").getAsString());
            if (m == null) m = spawn(o);
            if (m == null) continue;
            applyMonster(m, o, showEffects);
        }
        if (root.has("next")) nextSpawn = Math.max(nextSpawn, root.get("next").getAsInt());
        reorder(arr);
        afterApply();
    }

    private static void reorder(JsonArray arr) {
        MonsterGroup g = group();
        ArrayList<AbstractMonster> ordered = new ArrayList<>();
        for (JsonElement e : arr) {
            AbstractMonster m = byId(e.getAsJsonObject().get("id").getAsString());
            if (m != null && !ordered.contains(m)) ordered.add(m);
        }
        for (AbstractMonster m : g.monsters) if (!ordered.contains(m)) ordered.add(m);
        g.monsters.clear();
        g.monsters.addAll(ordered);
    }

    private static void applyMonster(AbstractMonster m, JsonObject o, boolean showEffects) {
        int oldHp = m.currentHealth;
        applyFields(m, AbstractCreature.class, o.getAsJsonObject("f"));
        m.maxHealth = o.get("maxHp").getAsInt();
        m.currentBlock = o.get("block").getAsInt();
        setHp(m, o.get("hp").getAsInt(), showEffects, oldHp);
        m.halfDead = o.get("halfDead").getAsBoolean();
        if (o.has("move")) {
            JsonObject mo = o.getAsJsonObject("move");
            AbstractMonster.Intent intent = mo.get("i").getAsString().isEmpty() ? null
                    : AbstractMonster.Intent.valueOf(mo.get("i").getAsString());
            setMove(m, new EnemyMoveInfo(mo.get("n").getAsByte(), intent, mo.get("d").getAsInt(),
                    mo.get("x").getAsInt(), mo.get("md").getAsBoolean()));
        }
        JsonArray dmg = o.getAsJsonArray("damage");
        if (dmg != null) {
            for (int i = 0; i < dmg.size(); i++) {
                JsonObject dj = dmg.get(i).getAsJsonObject();
                DamageInfo.DamageType t = DamageInfo.DamageType.valueOf(dj.get("t").getAsString());
                if (i >= m.damage.size()) m.damage.add(new DamageInfo(m, dj.get("b").getAsInt(), t));
                DamageInfo d = m.damage.get(i);
                d.base = dj.get("b").getAsInt();
                d.output = dj.get("o").getAsInt();
            }
            while (m.damage.size() > dmg.size()) m.damage.remove(m.damage.size() - 1);
        }
        applyPowers(m, o.getAsJsonArray("powers"));
        if (o.get("dead").getAsBoolean()) kill(m);
        else if (m.isDying || m.isDead) revive(m);
        if (o.get("escaped").getAsBoolean() && !m.escaped) m.escaped = true;
    }

    private static void setHp(AbstractMonster m, int hp, boolean showEffects, int oldHp) {
        m.currentHealth = Math.max(0, Math.min(hp, m.maxHealth));
        if (showEffects && m.currentHealth < oldHp) {
            AbstractDungeon.effectList.add(new FlashAtkImgEffect(m.hb.cX, m.hb.cY,
                    AbstractGameAction.AttackEffect.BLUNT_LIGHT));
            AbstractDungeon.effectList.add(new DamageNumberEffect(m, m.hb.cX, m.hb.cY, oldHp - m.currentHealth));
            m.tint.color.set(Color.RED);
            m.tint.changeColor(Color.WHITE.cpy());
        }
        m.healthBarUpdatedEvent();
    }

    private static void kill(AbstractMonster m) {
        if (m.isDying || m.isDead) return;
        m.currentHealth = 0;
        m.isDying = true;
        m.hideHealthBar();
        // updateDeathAnimation() finishes the death (and ends the battle if everyone is dead)
    }

    private static void revive(AbstractMonster m) {
        m.isDying = false;
        m.isDead = false;
        try {
            Field t = AbstractMonster.class.getDeclaredField("deathTimer");
            t.setAccessible(true);
            t.setFloat(m, 0f);
            Field f = AbstractMonster.class.getDeclaredField("tintFadeOutCalled");
            f.setAccessible(true);
            f.setBoolean(m, false);
        } catch (Throwable ignored) {
        }
        m.tint.color.a = 1f;
        m.showHealthBar();
    }

    private static void applyPowers(AbstractMonster m, JsonArray pw) {
        if (pw == null) return;
        ArrayList<AbstractPower> old = new ArrayList<>(m.powers);
        ArrayList<AbstractPower> out = new ArrayList<>();
        for (JsonElement e : pw) {
            JsonObject pj = e.getAsJsonObject();
            String cls = pj.get("cls").getAsString();
            String pid = pj.get("pid").isJsonNull() ? null : pj.get("pid").getAsString();
            AbstractPower p = null;
            for (AbstractPower q : old) {
                if (q.getClass().getName().equals(cls) && (pid == null || pid.equals(q.ID))) {
                    p = q;
                    break;
                }
            }
            if (p != null) old.remove(p);
            else p = makePower(cls, m, pj.get("amount").getAsInt());
            if (p == null) continue;
            applyFields(p, AbstractPower.class, pj.getAsJsonObject("f"));
            p.owner = m;
            p.amount = pj.get("amount").getAsInt();
            try {
                p.updateDescription();
            } catch (Throwable ignored) {
            }
            out.add(p);
        }
        m.powers.clear();
        m.powers.addAll(out);
    }

    private static AbstractPower makePower(String cls, AbstractMonster owner, int amount) {
        try {
            Class<?> c = Class.forName(cls, true, MonsterSync.class.getClassLoader());
            Constructor<?>[] cs = c.getConstructors();
            java.util.Arrays.sort(cs, (a, b) -> a.getParameterCount() - b.getParameterCount());
            for (Constructor<?> k : cs) {
                Object[] args = new Object[k.getParameterCount()];
                Class<?>[] ts = k.getParameterTypes();
                boolean firstCreature = true;
                for (int i = 0; i < ts.length; i++) {
                    if (AbstractCreature.class.isAssignableFrom(ts[i])) {
                        args[i] = firstCreature ? owner : AbstractDungeon.player;
                        firstCreature = false;
                    } else if (ts[i] == int.class) args[i] = amount;
                    else if (ts[i] == boolean.class) args[i] = false;
                    else if (ts[i] == float.class) args[i] = 0f;
                    else args[i] = null;
                }
                try {
                    return (AbstractPower) k.newInstance(args);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            MP.logger.error("could not create power " + cls, t);
        }
        return null;
    }

    private static AbstractMonster spawn(JsonObject o) {
        String cls = o.get("cls").getAsString();
        Random[] rngs = {AbstractDungeon.aiRng, AbstractDungeon.monsterHpRng, AbstractDungeon.monsterRng,
                AbstractDungeon.miscRng};
        long[][] saved = RngSync.save(rngs);
        try {
            Class<?> c = Class.forName(cls, true, MonsterSync.class.getClassLoader());
            AbstractMonster m = null;
            Constructor<?>[] cs = c.getConstructors();
            java.util.Arrays.sort(cs, (a, b) -> a.getParameterCount() - b.getParameterCount());
            for (Constructor<?> k : cs) {
                Object[] args = new Object[k.getParameterCount()];
                Class<?>[] ts = k.getParameterTypes();
                for (int i = 0; i < ts.length; i++) {
                    if (ts[i] == float.class) args[i] = 0f;
                    else if (ts[i] == int.class) args[i] = 0;
                    else if (ts[i] == boolean.class) args[i] = false;
                    else args[i] = null;
                }
                try {
                    m = (AbstractMonster) k.newInstance(args);
                    break;
                } catch (Throwable ignored) {
                }
            }
            if (m == null) return null;
            MPFields.Monster.id.set(m, o.get("id").getAsString());
            MultiCreature.Field.currentRow.set(m, o.get("row").getAsInt());
            m.drawX = o.get("bx").getAsFloat();
            m.drawY = o.get("by").getAsFloat();
            MPFields.Monster.layout.set(m, new float[]{0f, 0f});
            applyLayout(m);
            m.init();
            m.showHealthBar();
            group().monsters.add(m);
            return m;
        } catch (Throwable t) {
            MP.logger.error("could not spawn " + cls, t);
            return null;
        } finally {
            RngSync.restore(rngs, saved);
        }
    }

    private static void afterApply() {
        MonsterGroup g = group();
        for (AbstractMonster m : g.monsters) {
            if (m.isDeadOrEscaped()) continue;
            try {
                m.applyPowers();
                m.createIntent();
            } catch (Throwable ignored) {
            }
        }
        if (AbstractDungeon.player != null) {
            AbstractDungeon.player.hand.applyPowers();
            AbstractDungeon.player.hand.glowCheck();
        }
    }

    // ---------- deltas ----------

    /** What changed between two captures, for additive merging into other games. */
    public static JsonObject diff(JsonObject before, JsonObject after) {
        HashMap<String, JsonObject> b = index(before);
        JsonObject d = new JsonObject();
        JsonArray changes = new JsonArray();
        for (JsonElement e : after.getAsJsonArray("monsters")) {
            JsonObject a = e.getAsJsonObject();
            String id = a.get("id").getAsString();
            JsonObject o = b.get(id);
            if (o == null) { // spawned locally
                JsonObject c = new JsonObject();
                c.addProperty("id", id);
                c.add("spawn", a);
                changes.add(c);
                continue;
            }
            JsonObject c = new JsonObject();
            c.addProperty("id", id);
            boolean any = false;
            int dhp = a.get("hp").getAsInt() - o.get("hp").getAsInt();
            if (dhp != 0) { c.addProperty("dhp", dhp); any = true; }
            int dmax = a.get("maxHp").getAsInt() - o.get("maxHp").getAsInt();
            if (dmax != 0) { c.addProperty("dmax", dmax); any = true; }
            int dbl = a.get("block").getAsInt() - o.get("block").getAsInt();
            if (dbl != 0) { c.addProperty("dblock", dbl); any = true; }
            if (a.get("dead").getAsBoolean() != o.get("dead").getAsBoolean()) {
                c.addProperty("dead", a.get("dead").getAsBoolean());
                any = true;
            }
            JsonObject setF = new JsonObject();
            JsonObject af = a.getAsJsonObject("f"), of = o.getAsJsonObject("f");
            for (Map.Entry<String, JsonElement> fe : af.entrySet()) {
                if (!fe.getValue().equals(of.get(fe.getKey()))) setF.add(fe.getKey(), fe.getValue());
            }
            if (setF.entrySet().size() > 0) { c.add("f", setF); any = true; }
            if (a.has("move") && !a.get("move").equals(o.get("move"))) {
                c.add("move", a.get("move"));
                c.add("damage", a.get("damage"));
                any = true;
            }
            // powers keyed by class name
            HashMap<String, JsonObject> bp = new HashMap<>();
            for (JsonElement pe : o.getAsJsonArray("powers")) bp.put(pkey(pe.getAsJsonObject()), pe.getAsJsonObject());
            JsonArray pch = new JsonArray();
            HashSet<String> seen = new HashSet<>();
            for (JsonElement pe : a.getAsJsonArray("powers")) {
                JsonObject ap = pe.getAsJsonObject();
                String k = pkey(ap);
                seen.add(k);
                JsonObject op = bp.get(k);
                if (op == null) {
                    JsonObject x = new JsonObject();
                    x.addProperty("k", k);
                    x.add("add", ap);
                    pch.add(x);
                } else {
                    int da = ap.get("amount").getAsInt() - op.get("amount").getAsInt();
                    JsonObject pf = new JsonObject();
                    for (Map.Entry<String, JsonElement> fe : ap.getAsJsonObject("f").entrySet()) {
                        if (fe.getKey().endsWith(".amount")) continue;
                        if (!fe.getValue().equals(op.getAsJsonObject("f").get(fe.getKey())))
                            pf.add(fe.getKey(), fe.getValue());
                    }
                    if (da != 0 || pf.entrySet().size() > 0) {
                        JsonObject x = new JsonObject();
                        x.addProperty("k", k);
                        x.add("full", ap);
                        x.addProperty("da", da);
                        x.add("f", pf);
                        pch.add(x);
                    }
                }
            }
            for (String k : bp.keySet()) {
                if (seen.contains(k)) continue;
                JsonObject x = new JsonObject();
                x.addProperty("k", k);
                x.addProperty("remove", true);
                pch.add(x);
            }
            if (pch.size() > 0) { c.add("powers", pch); any = true; }
            if (any) changes.add(c);
        }
        d.add("changes", changes);
        if (after.has("next")) d.add("next", after.get("next"));
        return d;
    }

    public static boolean isEmpty(JsonObject delta) {
        return delta.getAsJsonArray("changes").size() == 0;
    }

    private static String pkey(JsonObject p) {
        return p.get("cls").getAsString() + "|" + (p.get("pid").isJsonNull() ? "" : p.get("pid").getAsString());
    }

    private static HashMap<String, JsonObject> index(JsonObject state) {
        HashMap<String, JsonObject> h = new HashMap<>();
        for (JsonElement e : state.getAsJsonArray("monsters")) {
            JsonObject o = e.getAsJsonObject();
            h.put(o.get("id").getAsString(), o);
        }
        return h;
    }

    /** Merge a delta produced in another game into the local enemies. */
    public static void applyDelta(JsonObject d, boolean showEffects) {
        MonsterGroup g = group();
        if (g == null || d == null) return;
        assignIds();
        for (JsonElement e : d.getAsJsonArray("changes")) {
            JsonObject c = e.getAsJsonObject();
            if (c.has("spawn")) {
                if (byId(c.get("id").getAsString()) == null) {
                    AbstractMonster m = spawn(c.getAsJsonObject("spawn"));
                    if (m != null) applyMonster(m, c.getAsJsonObject("spawn"), false);
                }
                continue;
            }
            AbstractMonster m = byId(c.get("id").getAsString());
            if (m == null) continue;
            int oldHp = m.currentHealth;
            if (c.has("f")) applyFields(m, AbstractCreature.class, c.getAsJsonObject("f"));
            if (c.has("dmax")) m.maxHealth += c.get("dmax").getAsInt();
            if (c.has("dblock")) m.currentBlock = Math.max(0, m.currentBlock + c.get("dblock").getAsInt());
            if (c.has("dhp")) setHp(m, m.currentHealth + c.get("dhp").getAsInt(), showEffects, oldHp);
            if (c.has("move")) {
                JsonObject mo = c.getAsJsonObject("move");
                AbstractMonster.Intent intent = mo.get("i").getAsString().isEmpty() ? null
                        : AbstractMonster.Intent.valueOf(mo.get("i").getAsString());
                setMove(m, new EnemyMoveInfo(mo.get("n").getAsByte(), intent, mo.get("d").getAsInt(),
                        mo.get("x").getAsInt(), mo.get("md").getAsBoolean()));
            }
            if (c.has("powers")) {
                for (JsonElement pe : c.getAsJsonArray("powers")) {
                    JsonObject x = pe.getAsJsonObject();
                    String k = x.get("k").getAsString();
                    AbstractPower local = null;
                    for (AbstractPower p : m.powers) {
                        String lk = p.getClass().getName() + "|" + (p.ID == null ? "" : p.ID);
                        if (lk.equals(k)) {
                            local = p;
                            break;
                        }
                    }
                    if (x.has("remove")) {
                        if (local != null) m.powers.remove(local);
                    } else if (x.has("add") || local == null) {
                        JsonObject full = x.has("add") ? x.getAsJsonObject("add") : x.getAsJsonObject("full");
                        if (local == null) {
                            AbstractPower p = makePower(full.get("cls").getAsString(), m, full.get("amount").getAsInt());
                            if (p == null) continue;
                            applyFields(p, AbstractPower.class, full.getAsJsonObject("f"));
                            p.owner = m;
                            p.amount = full.get("amount").getAsInt();
                            p.updateDescription();
                            m.powers.add(p);
                        } else {
                            local.amount += full.get("amount").getAsInt();
                            local.updateDescription();
                        }
                    } else {
                        local.amount += x.get("da").getAsInt();
                        applyFields(local, AbstractPower.class, x.getAsJsonObject("f"));
                        local.updateDescription();
                    }
                }
            }
            if (m.currentHealth <= 0 && !m.halfDead || (c.has("dead") && c.get("dead").getAsBoolean())) kill(m);
        }
        if (d.has("next")) nextSpawn = Math.max(nextSpawn, d.get("next").getAsInt());
        afterApply();
    }

    /** True while the room is a multi-row multiplayer combat. */
    public static boolean active() {
        return MP.inRun && MP.playerCount() > 1 && AbstractDungeon.currMapNode != null
                && AbstractDungeon.getCurrRoom() != null && AbstractDungeon.getCurrRoom() == sharedRoom
                && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT;
    }
}

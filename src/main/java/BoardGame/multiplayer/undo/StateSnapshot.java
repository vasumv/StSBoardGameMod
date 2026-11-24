package BoardGame.multiplayer.undo;

import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.ModInfo;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * A content-agnostic snapshot of combat state.
 *
 * Walks the object graph from the player, the room, the action manager, the random
 * generators and the mod's static fields, and records a shallow copy of every field
 * of every object reached (arrays by content). restore() writes the recorded values
 * back into the same objects, so object identity is preserved and no per-card or
 * per-power code is needed. Rendering resources are treated as opaque references.
 *
 * Only valid to capture and restore while the action queue is idle.
 */
public final class StateSnapshot {
    private final IdentityHashMap<Object, Object> saved = new IdentityHashMap<>();
    private final ArrayList<Object[]> statics = new ArrayList<>(); // {Field, value}
    public long captureNanos;

    private StateSnapshot() {
    }

    public int objectCount() {
        return saved.size();
    }

    public static StateSnapshot capture() {
        long t0 = System.nanoTime();
        StateSnapshot s = new StateSnapshot();
        ArrayDeque<Object> todo = new ArrayDeque<>();
        s.enqueue(AbstractDungeon.player, todo);
        s.enqueue(AbstractDungeon.getCurrRoom(), todo);
        s.enqueue(AbstractDungeon.actionManager, todo);
        for (Field f : staticRoots()) {
            try {
                Object v = f.get(null);
                if (!Modifier.isFinal(f.getModifiers())) s.statics.add(new Object[]{f, v});
                s.enqueue(v, todo);
            } catch (Throwable ignored) {
            }
        }
        while (!todo.isEmpty()) s.record(todo.poll(), todo);
        s.captureNanos = System.nanoTime() - t0;
        return s;
    }

    public void restore() {
        try {
            for (Map.Entry<Object, Object> e : saved.entrySet()) {
                Object o = e.getKey();
                Class<?> c = o.getClass();
                if (c.isArray()) {
                    System.arraycopy(e.getValue(), 0, o, 0, Array.getLength(o));
                } else {
                    Field[] fs = fieldsOf(c);
                    Object[] vals = (Object[]) e.getValue();
                    for (int i = 0; i < fs.length; i++) {
                        if (!Modifier.isFinal(fs[i].getModifiers())) fs[i].set(o, vals[i]);
                    }
                }
            }
            for (Object[] st : statics) ((Field) st[0]).set(null, st[1]);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    /** Per-class object counts, largest first; for tuning what the walk reaches. */
    public String stats(int top) {
        HashMap<String, Integer> counts = new HashMap<>();
        for (Object o : saved.keySet()) counts.merge(o.getClass().getName(), 1, Integer::sum);
        ArrayList<Map.Entry<String, Integer>> l = new ArrayList<>(counts.entrySet());
        l.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder(saved.size() + " objects, " + statics.size() + " statics, "
                + (captureNanos / 1000) + "us:");
        for (int i = 0; i < Math.min(top, l.size()); i++)
            sb.append(' ').append(l.get(i).getKey()).append('=').append(l.get(i).getValue());
        return sb.toString();
    }

    private void enqueue(Object o, ArrayDeque<Object> todo) {
        if (o == null || saved.containsKey(o) || !tracked(o)) return;
        saved.put(o, o); // placeholder, replaced in record()
        todo.add(o);
    }

    private void record(Object o, ArrayDeque<Object> todo) {
        Class<?> c = o.getClass();
        try {
            if (c.isArray()) {
                int n = Array.getLength(o);
                Object copy = Array.newInstance(c.getComponentType(), n);
                System.arraycopy(o, 0, copy, 0, n);
                saved.put(o, copy);
                if (!c.getComponentType().isPrimitive()) {
                    Object[] arr = (Object[]) o;
                    for (Object el : arr) enqueue(el, todo);
                }
                return;
            }
            Field[] fs = fieldsOf(c);
            Object[] vals = new Object[fs.length];
            for (int i = 0; i < fs.length; i++) {
                vals[i] = fs[i].get(o);
                if (!fs[i].getType().isPrimitive()) enqueue(vals[i], todo);
            }
            saved.put(o, vals);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    // ---- what the walk descends into ----

    private static final HashMap<Class<?>, Boolean> TRACKED = new HashMap<>();
    private static final String CC = "com.megacrit.cardcrawl.";
    private static final String[] CC_PACKAGES = {"actions.", "cards.", "characters.", "monsters.", "powers.",
            "relics.", "orbs.", "stances.", "potions.", "random.", "rooms.", "rewards.", "events.", "blights.",
            "core.AbstractCreature", "core.EnergyManager", "helpers.Hitbox", "vfx.TintEffect"};
    private static final String[] OTHER_PREFIXES = {"com.evacipated.cardcrawl.mod.stslib.", "basemod.abstracts.",
            "basemod.cardmods.", "com.badlogic.gdx.math.", "com.badlogic.gdx.graphics.Color"};
    private static final String[] GDX_UTILS = {"Array", "ObjectMap", "ObjectSet", "IntArray", "FloatArray",
            "IntMap", "OrderedMap"};

    private static boolean tracked(Object o) {
        Class<?> c = o.getClass();
        Boolean b = TRACKED.get(c);
        if (b == null) {
            b = computeTracked(o, c);
            TRACKED.put(c, b);
        }
        return b;
    }

    private static boolean computeTracked(Object o, Class<?> c) {
        if (c.isArray()) return true;
        if (o instanceof Enum || o instanceof String || o instanceof Number || o instanceof Boolean
                || o instanceof Character || o instanceof Class || o instanceof Thread
                || o instanceof ClassLoader) return false;
        String n = c.getName();
        if (n.startsWith("BoardGame.")) return !n.startsWith("BoardGame.multiplayer.");
        if (n.startsWith(CC)) {
            String rest = n.substring(CC.length());
            for (String p : CC_PACKAGES) if (rest.startsWith(p)) return true;
            return false;
        }
        if (n.startsWith("java.util.")) {
            // collections only: java.util.X..., not sub-packages
            char ch = n.charAt("java.util.".length());
            if (!Character.isUpperCase(ch)) return false;
            return !(n.startsWith("java.util.Locale") || n.startsWith("java.util.ResourceBundle")
                    || n.startsWith("java.util.Timer") || n.startsWith("java.util.TimeZone")
                    || n.startsWith("java.util.Calendar") || n.startsWith("java.util.Currency")
                    || n.startsWith("java.util.Scanner") || n.startsWith("java.util.Formatter")
                    || n.startsWith("java.util.ServiceLoader") || n.startsWith("java.util.Properties"));
        }
        for (String p : OTHER_PREFIXES) if (n.startsWith(p)) return true;
        if (n.startsWith("com.badlogic.gdx.utils.")) {
            String rest = n.substring("com.badlogic.gdx.utils.".length());
            for (String g : GDX_UTILS) if (rest.equals(g)) return true;
        }
        return false;
    }

    private static final HashMap<Class<?>, Field[]> FIELDS = new HashMap<>();

    private static Field[] fieldsOf(Class<?> c) {
        Field[] fs = FIELDS.get(c);
        if (fs == null) {
            ArrayList<Field> l = new ArrayList<>();
            for (Class<?> k = c; k != null; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    l.add(f);
                }
            }
            fs = l.toArray(new Field[0]);
            FIELDS.put(c, fs);
        }
        return fs;
    }

    // ---- static roots ----

    private static final String[] DUNGEON_RNGS = {"monsterRng", "cardRng", "relicRng", "potionRng", "monsterHpRng",
            "aiRng", "shuffleRng", "cardRandomRng", "miscRng", "eventRng", "merchantRng", "treasureRng"};
    private static ArrayList<Field> vanillaStatics;
    private static ArrayList<String> modClassNames;
    private static final HashMap<String, Field[]> MOD_STATICS = new HashMap<>();
    private static Method findLoaded;
    private static sun.misc.Unsafe unsafe;

    private static ArrayList<Field> staticRoots() {
        if (vanillaStatics == null) {
            vanillaStatics = new ArrayList<>();
            for (String n : DUNGEON_RNGS) addStatic(AbstractDungeon.class, n);
            addAllStatics(GameActionManager.class, vanillaStatics, false);
            addStatic(EnergyPanel.class, "totalCount");
            addStatic(DrawCardAction.class, "drawnCards");
        }
        ArrayList<Field> out = new ArrayList<>(vanillaStatics);
        // Statics of every mod class that is loaded and initialised. A class that has not
        // been initialised cannot hold mutated state yet.
        ClassLoader cl = StateSnapshot.class.getClassLoader();
        for (String name : modClassNames()) {
            Field[] fs = MOD_STATICS.get(name);
            if (fs == null) {
                try {
                    Class<?> c = (Class<?>) findLoaded.invoke(cl, name);
                    if (c == null || unsafe.shouldBeInitialized(c)) continue;
                    ArrayList<Field> l = new ArrayList<>();
                    addAllStatics(c, l, true);
                    fs = l.toArray(new Field[0]);
                } catch (Throwable t) {
                    fs = new Field[0];
                }
                MOD_STATICS.put(name, fs);
            }
            for (Field f : fs) out.add(f);
        }
        return out;
    }

    private static void addStatic(Class<?> c, String name) {
        try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            vanillaStatics.add(f);
        } catch (Throwable ignored) {
        }
    }

    private static void addAllStatics(Class<?> c, ArrayList<Field> out, boolean includeFinalContainers) {
        for (Field f : c.getDeclaredFields()) {
            int m = f.getModifiers();
            if (!Modifier.isStatic(m)) continue;
            if (Modifier.isFinal(m) && (!includeFinalContainers || f.getType().isPrimitive()
                    || f.getType() == String.class)) continue;
            try {
                f.setAccessible(true);
                out.add(f);
            } catch (Throwable ignored) {
            }
        }
    }

    private static ArrayList<String> modClassNames() {
        if (modClassNames != null) return modClassNames;
        modClassNames = new ArrayList<>();
        try {
            Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            uf.setAccessible(true);
            unsafe = (sun.misc.Unsafe) uf.get(null);
            findLoaded = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
            findLoaded.setAccessible(true);
            for (ModInfo info : Loader.MODINFOS) {
                if (!"BoardGame".equals(info.ID) || info.jarURL == null) continue;
                try (JarFile jar = new JarFile(new java.io.File(info.jarURL.toURI()))) {
                    Enumeration<JarEntry> en = jar.entries();
                    while (en.hasMoreElements()) {
                        String n = en.nextElement().getName();
                        if (!n.startsWith("BoardGame/") || !n.endsWith(".class")) continue;
                        if (n.startsWith("BoardGame/multiplayer/")) continue;
                        modClassNames.add(n.substring(0, n.length() - 6).replace('/', '.'));
                    }
                }
            }
        } catch (Throwable t) {
            UndoManager.logger.error("could not list mod classes for static snapshot", t);
        }
        return modClassNames;
    }
}

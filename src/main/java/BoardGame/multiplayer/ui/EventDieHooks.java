package BoardGame.multiplayer.ui;

import com.evacipated.cardcrawl.modthespire.lib.SpireInstrumentPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2;
import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.MethodCall;

/**
 * Shows the die for events that roll one for you alone: the roll itself, each die relic the event
 * uses for you, and each change you make with one (see {@link EventDie}).
 */
public class EventDieHooks {
    private static ExprEditor edit() {
        return new ExprEditor() {
            @Override
            public void edit(MethodCall m) throws CannotCompileException {
                if (m.getMethodName().equals("random") && m.getClassName().equals("com.megacrit.cardcrawl.random.Random")
                        && m.getSignature().equals("(II)I")) {
                    m.replace("{ $_ = $proceed($$); BoardGame.multiplayer.ui.EventDie.rolled($_, $1, $2); }");
                } else if (m.getMethodName().equals("flash") && m.getSignature().equals("()V")
                        && m.getClassName().startsWith("com.megacrit.cardcrawl.relics.")) {
                    m.replace("{ $proceed($$); BoardGame.multiplayer.ui.EventDie.relicUsed($0.relicId); }");
                }
            }

            @Override
            public void edit(FieldAccess f) throws CannotCompileException {
                if (f.isWriter() && f.getSignature().equals("I")
                        && (f.getFieldName().equals("pendingReward") || f.getFieldName().equals("result")))
                    f.replace("{ $proceed($$); BoardGame.multiplayer.ui.EventDie.changed($1); }");
            }
        };
    }

    @SpirePatch2(clz = BoardGame.events.BGTheJoust.class, method = "buttonEffect")
    public static class Joust {
        @SpireInstrumentPatch
        public static ExprEditor e() {
            return edit();
        }
    }

    @SpirePatch2(clz = BoardGame.events.BGTheMausoleum.class, method = "getReward", paramtypez = {boolean.class, boolean.class})
    public static class Mausoleum {
        @SpireInstrumentPatch
        public static ExprEditor e() {
            return edit();
        }
    }

    @SpirePatch2(clz = BoardGame.events.BGAccursedBlacksmith.class, method = "getReward", paramtypez = {boolean.class, boolean.class})
    public static class Blacksmith {
        @SpireInstrumentPatch
        public static ExprEditor e() {
            return edit();
        }
    }

    @SpirePatch2(clz = BoardGame.events.BGScrapOoze.class, method = "buttonEffect")
    public static class Ooze {
        @SpireInstrumentPatch
        public static ExprEditor e() {
            return edit();
        }
    }

    @SpirePatch2(clz = BoardGame.events.BGScrapOoze.class, method = "takeReward")
    public static class OozeReward {
        @SpireInstrumentPatch
        public static ExprEditor e() {
            return edit();
        }
    }

    /** Dead Adventurer's roll is already hooked (shared rolls); only its changes are added here. */
    @SpirePatch2(clz = BoardGame.events.BGDeadAdventurer.class, method = "buttonEffect")
    public static class DeadAdventurerChanges {
        @SpireInstrumentPatch
        public static ExprEditor e() {
            return new ExprEditor() {
                @Override
                public void edit(FieldAccess f) throws CannotCompileException {
                    if (f.isWriter() && f.getFieldName().equals("pendingReward"))
                        f.replace("{ $proceed($$); BoardGame.multiplayer.ui.EventDie.changed($1); }");
                }
            };
        }
    }
}

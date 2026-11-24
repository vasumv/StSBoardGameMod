package BoardGame.multiplayer;

import com.google.gson.JsonObject;

/** In-run shared logic driven by the message stream. Filled in by later milestones. */
public class Session {
    public static void reset() {
    }

    /** False to leave the message at the head of the stream until the local game is ready for it. */
    public static boolean canProcess(JsonObject m) {
        return true;
    }

    public static void handle(String type, int from, JsonObject m) {
        MP.logger.info("unhandled message " + m);
    }

    public static void update() {
    }

    public static void toast(String text) {
        MP.logger.info("[toast] " + text);
    }

    public static String status() {
        return "";
    }
}

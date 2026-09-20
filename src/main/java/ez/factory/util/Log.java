package ez.factory.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Tiny timestamped console logger (no dependencies). */
public final class Log {
    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    public static void info(String tag, String msg) { print("INFO ", tag, msg); }
    public static void warn(String tag, String msg) { print("WARN ", tag, msg); }
    public static void err(String tag, String msg) { print("ERROR", tag, msg); }

    private static synchronized void print(String lvl, String tag, String msg) {
        System.out.println("[" + LocalDateTime.now().format(F) + "] " + lvl + " [" + tag + "] " + msg);
    }
}

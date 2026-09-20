package ez.factory.util;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs external processes (ffmpeg) with a timeout and captured output. */
public final class Exec {

    public static final class Result {
        public final int exit;
        public final String out;
        Result(int exit, String out) { this.exit = exit; this.out = out; }
        public boolean ok() { return exit == 0; }
    }

    public static Result run(List<String> cmd, long timeoutSec) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            throw new IOException("Cannot start command: " + String.join(" ", cmd) + " (" + e.getMessage() + ")", e);
        }
        StringBuilder sb = new StringBuilder();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
                if (sb.length() > 200_000) break;
            }
        }
        try {
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("Timeout after " + timeoutSec + "s: " + String.join(" ", cmd));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted: " + String.join(" ", cmd), e);
        }
        return new Result(p.exitValue(), sb.toString());
    }
}

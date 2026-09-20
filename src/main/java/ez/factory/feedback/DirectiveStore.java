package ez.factory.feedback;

import ez.factory.util.IO;
import ez.factory.util.Json;
import ez.factory.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Feedback memory (data/directives.json) + folder inbox (feedback/inbox).
 *
 * Two ways in, one memory:
 *   1. HTTP API (FeedbackServer) — used by the owner's tools and later by the QA module.
 *   2. Folder drop — put note.txt / note.json (+ optional note.png screenshot) into
 *      feedback/inbox; the daemon scans it and converts files into directives.
 *
 * The planner reads active directives on every run; each run decrements runsLeft so old
 * instructions retire on their own.
 */
public final class DirectiveStore {

    private final Path file;
    private final Path inbox;
    private final List<Directive> all = new ArrayList<>();

    public DirectiveStore(Path file, Path inbox) throws IOException {
        this.file = file;
        this.inbox = inbox;
        IO.ensureDir(inbox);
        if (file.toFile().exists()) {
            for (Object o : Json.list(Json.parseObject(IO.read(file)), "directives")) {
                all.add(Directive.fromJson(Json.obj(o)));
            }
        } else {
            save();
        }
    }

    public synchronized void save() throws IOException {
        Map<String, Object> root = new java.util.LinkedHashMap<>();
        List<Object> l = new ArrayList<>();
        for (Directive d : all) l.add(d.toJson());
        root.put("directives", l);
        IO.write(file, Json.pretty(root));
    }

    public synchronized Directive add(Directive d) throws IOException {
        if (d.id == null || d.id.isEmpty()) d.id = "d-" + UUID.randomUUID().toString().substring(0, 8);
        all.add(d);
        save();
        Log.info("feedback", "new directive from " + d.source + ": " + abbrev(d.text));
        return d;
    }

    public synchronized List<Directive> active() {
        List<Directive> out = new ArrayList<>();
        for (Directive d : all) if (d.active && d.runsLeft > 0) out.add(d);
        return out;
    }

    public synchronized List<Directive> list() { return new ArrayList<>(all); }

    /** Called once per executed post run. */
    public synchronized void tick() throws IOException {
        boolean changed = false;
        for (Directive d : all) {
            if (d.active && d.runsLeft > 0) {
                d.runsLeft--;
                if (d.runsLeft == 0) d.active = false;
                changed = true;
            }
        }
        if (changed) save();
    }

    /** Folder inbox scan: *.txt / *.md / *.json (+ optional same-basename image). */
    public synchronized int scanInbox(Path screenshotsDir) throws IOException {
        if (!Files.isDirectory(inbox)) return 0;
        int n = 0;
        try (Stream<Path> s = Files.list(inbox)) {
            List<Path> files = new ArrayList<>();
            s.forEach(files::add);
            for (Path f : files) {
                String name = f.getFileName().toString();
                String lower = name.toLowerCase();
                if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) continue; // handled with notes
                if (!(lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".json"))) continue;
                Directive d = new Directive();
                d.source = "human";
                String base = IO.baseName(name);
                Path shot = findSibling(inbox, base);
                if (shot != null) {
                    Path dest = screenshotsDir.resolve(shot.getFileName());
                    IO.move(shot, dest);
                    d.screenshotPath = dest.toString();
                }
                if (lower.endsWith(".json")) {
                    Map<String, Object> m = Json.parseObject(IO.read(f));
                    d.text = Json.str(m, "text", "");
                    d.source = Json.str(m, "source", "human");
                    d.runsLeft = Json.int_(m, "runs_left", d.runsLeft);
                    d.hints = Json.sub(m, "hints");
                } else {
                    d.text = IO.read(f).trim();
                }
                Path processed = inbox.resolve("processed");
                IO.move(f, processed.resolve(name));
                add(d);
                n++;
            }
        }
        return n;
    }

    private static Path findSibling(Path dir, String base) {
        for (String ext : new String[]{".png", ".jpg", ".jpeg"}) {
            Path p = dir.resolve(base + ext);
            if (p.toFile().exists()) return p;
        }
        return null;
    }

    private static String abbrev(String s) {
        s = s == null ? "" : s.replace('\n', ' ');
        return s.length() > 90 ? s.substring(0, 90) + "..." : s;
    }

    public static String newId() { return UUID.randomUUID().toString().substring(0, 8); }
}

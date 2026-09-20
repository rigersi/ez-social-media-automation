package ez.factory.plan;

import ez.factory.state.StateStore;
import ez.factory.util.IO;
import ez.factory.util.Json;
import ez.factory.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Topic cards = the factual seed material for posts (product, facts, hook angle, series).
 * Loaded from topics/seed-topics.json plus any topics/custom/*.json the owner or the QA
 * module drops in. Used topics are skipped until the pool is exhausted, then it recycles
 * oldest-first so the channel never stalls.
 */
public final class TopicPool {

    public final List<Map<String, Object>> topics = new ArrayList<>();

    public TopicPool(Path topicsDir, Path seedFile) throws IOException {
        List<Path> files = new ArrayList<>();
        if (seedFile.toFile().exists()) files.add(seedFile);
        Path custom = topicsDir.resolve("custom");
        if (Files.isDirectory(custom)) {
            try (Stream<Path> s = Files.list(custom)) {
                s.filter(p -> p.toString().endsWith(".json")).sorted().forEach(files::add);
            }
        }
        for (Path f : files) {
            Object o = Json.parse(IO.read(f));
            if (o instanceof List) for (Object t : Json.arr(o)) topics.add(Json.obj(t));
            else if (o instanceof Map) for (Object t : Json.list(o, "topics")) topics.add(Json.obj(t));
        }
        Log.info("topics", "loaded " + topics.size() + " topic cards");
    }

    /** Pick a topic suitable for the archetype, preferring unused ones. */
    public Map<String, Object> pick(Archetype a, StateStore state, java.util.Random rnd) {
        List<String> used = state.usedTopics();
        List<Map<String, Object>> fresh = new ArrayList<>();
        List<Map<String, Object>> stale = new ArrayList<>();
        for (Map<String, Object> t : topics) {
            String arch = Json.str(t, "archetype", "");
            boolean fits = arch.isEmpty() || arch.equalsIgnoreCase(a.id)
                    || Json.strList(t, "archetypes").contains(a.id);
            if (!fits) continue;
            (used.contains(Json.str(t, "id", "")) ? stale : fresh).add(t);
        }
        List<Map<String, Object>> pool = !fresh.isEmpty() ? fresh : (!stale.isEmpty() ? stale : topics);
        if (pool.isEmpty()) return null;
        return pool.get(rnd.nextInt(pool.size()));
    }

    public Map<String, Object> byId(String id) {
        for (Map<String, Object> t : topics) if (Json.str(t, "id", "").equals(id)) return t;
        return null;
    }
}

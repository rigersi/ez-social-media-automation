package ez.factory.state;

import ez.factory.util.IO;
import ez.factory.util.Json;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent memory of the factory (data/state.json):
 *  - series counters (episodic continuity: "Doomsday Tech File" -> next number)
 *  - recent archetype rotation (never repeat back-to-back, enforce rotation)
 *  - used topic ids (no repeats until the pool is exhausted)
 *  - the last scheduled slot (daemon never double-posts a slot)
 *  - rolling log of posted items (consumed later by the QA module)
 */
public final class StateStore {

    private final Path file;
    private final Map<String, Object> root;

    public StateStore(Path file) throws IOException {
        this.file = file;
        if (file.toFile().exists()) {
            this.root = Json.parseObject(IO.read(file));
        } else {
            this.root = new LinkedHashMap<>();
            root.put("seriesCounters", new LinkedHashMap<String, Object>());
            root.put("archetypeRecent", new ArrayList<Object>());
            root.put("usedTopics", new ArrayList<Object>());
            root.put("lastScheduledSlot", "");
            root.put("posts", new ArrayList<Object>());
            save();
        }
    }

    public synchronized void save() throws IOException {
        IO.write(file, Json.pretty(root));
    }

    // ---- series ----
    public synchronized int nextSeriesNumber(String seriesName) {
        Map<String, Object> counters = Json.sub(root, "seriesCounters");
        int n = Json.int_(counters, seriesName, 0) + 1;
        counters.put(seriesName, n);
        return n;
    }

    public synchronized int peekSeriesNumber(String seriesName) {
        return Json.int_(Json.sub(root, "seriesCounters"), seriesName, 0) + 1;
    }

    // ---- archetype rotation ----
    public synchronized List<String> recentArchetypes() {
        return Json.strList(root, "archetypeRecent");
    }

    public synchronized void pushArchetype(String a) {
        List<Object> l = Json.list(root, "archetypeRecent");
        l.add(a);
        while (l.size() > 8) l.remove(0);
    }

    // ---- topics ----
    public synchronized List<String> usedTopics() {
        return Json.strList(root, "usedTopics");
    }

    public synchronized void markTopicUsed(String id) {
        List<Object> l = Json.list(root, "usedTopics");
        if (!l.contains(id)) l.add(id);
    }

    // ---- slots ----
    public synchronized String lastScheduledSlot() {
        return Json.str(root, "lastScheduledSlot", "");
    }

    public synchronized void setLastScheduledSlot(String iso) {
        root.put("lastScheduledSlot", iso);
    }

    // ---- post log ----
    public synchronized void recordPost(Map<String, Object> rec) {
        rec.put("recordedAt", Instant.now().toString());
        List<Object> l = Json.list(root, "posts");
        l.add(rec);
        while (l.size() > 300) l.remove(0);
    }

    public synchronized List<Object> recentPosts(int n) {
        List<Object> l = Json.list(root, "posts");
        int from = Math.max(0, l.size() - n);
        return new ArrayList<>(l.subList(from, l.size()));
    }
}

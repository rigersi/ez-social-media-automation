package ez.factory.config;

import ez.factory.util.IO;
import ez.factory.util.Json;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Typed view over config/config.json. Unknown keys are ignored; missing keys get sane defaults. */
public final class Config {

    public final Map<String, Object> raw;

    // brand / canvas
    public final String brandName;
    public final Path logoPath;
    public final int logoMargin;
    public final int logoMaxWidth;
    public final int canvasW;
    public final int canvasH;

    // schedule
    public final String timezone;
    public final List<String> slotTimes;      // "10:00", "18:00"
    public final int leadMinutes;             // pipeline runs this many minutes before the slot
    public final int pollSeconds;

    // llm
    public final String llmProvider;          // ollama | openai | none
    public final String ollamaUrl;
    public final String llmModel;
    public final String visionModel;          // optional, for screenshot feedback (ollama vision model)
    public final String openaiBaseUrl;
    public final String openaiApiKey;
    public final String openaiModel;
    public final double llmTemperature;

    // comfyui
    public final String artMode;              // auto | comfy | fallback
    public final String comfyUrl;
    public final Path comfyWorkflowPath;
    public final String comfyPromptNodeId;
    public final String comfyPromptField;
    public final String comfyBaseImageNodeId; // LoadImage node fed with the deterministic base layout ("" = none)
    public final int comfyTimeoutSec;

    // video
    public final boolean videoEnabled;
    public final String ffmpegPath;
    public final int videoSeconds;
    public final int videoFps;

    // postiz / publishing
    public final String postizBaseUrl;
    public final String postizApiKey;
    public final List<String> postizChannels; // identifier filter, empty = all connected
    public final String youtubePlaylistId;
    public final String publishMode;          // dry-run | live
    public final Path outbox;

    // feedback
    public final boolean feedbackEnabled;
    public final int feedbackPort;
    public final String feedbackToken;
    public final Path feedbackInbox;
    public final Path feedbackScreenshots;
    public final int directiveDefaultRuns;

    // hashtags
    public final Path hashtagPoolPath;
    public final int hashtagMin;
    public final int hashtagMax;

    // dirs / topics
    public final Path dataDir;
    public final Path outDir;
    public final Path topicsDir;

    private Config(Map<String, Object> r) {
        this.raw = r;
        Map<String, Object> brand = Json.sub(r, "brand");
        brandName = Json.str(brand, "name", "EZ Good Finds");
        logoPath = Paths.get(Json.str(brand, "logoPath", "brand/logo.png"));
        logoMargin = Json.int_(brand, "logoMargin", 36);
        logoMaxWidth = Json.int_(brand, "logoMaxWidth", 300);

        Map<String, Object> canvas = Json.sub(r, "canvas");
        canvasW = Json.int_(canvas, "width", 1080);
        canvasH = Json.int_(canvas, "height", 1920);

        Map<String, Object> slots = Json.sub(r, "slots");
        timezone = Json.str(slots, "timezone", "Europe/Tirane");
        List<String> t = Json.strList(slots, "times");
        if (t.isEmpty()) { t = new ArrayList<>(); t.add("10:00"); t.add("18:00"); }
        slotTimes = t;
        leadMinutes = Json.int_(slots, "leadMinutes", 90);
        pollSeconds = Json.int_(slots, "pollSeconds", 30);

        Map<String, Object> llm = Json.sub(r, "llm");
        llmProvider = Json.str(llm, "provider", "ollama");
        ollamaUrl = Json.str(llm, "ollamaUrl", "http://127.0.0.1:11434");
        llmModel = Json.str(llm, "model", "qwen2.5:7b");
        visionModel = Json.str(llm, "visionModel", "");
        Map<String, Object> oa = Json.sub(llm, "openaiCompatible");
        openaiBaseUrl = Json.str(oa, "baseUrl", "http://127.0.0.1:1234/v1");
        openaiApiKey = Json.str(oa, "apiKey", "");
        openaiModel = Json.str(oa, "model", "");
        llmTemperature = Json.dbl(llm, "temperature", 0.85);

        Map<String, Object> comfy = Json.sub(r, "comfyui");
        artMode = Json.str(comfy, "mode", "auto");
        comfyUrl = Json.str(comfy, "url", "http://127.0.0.1:8188");
        comfyWorkflowPath = Paths.get(Json.str(comfy, "workflowPath", "comfyui/workflow.json"));
        comfyPromptNodeId = Json.str(comfy, "promptNodeId", "6");
        comfyPromptField = Json.str(comfy, "promptField", "text");
        comfyBaseImageNodeId = Json.str(comfy, "baseImageNodeId", "");
        comfyTimeoutSec = Json.int_(comfy, "timeoutSeconds", 900);

        Map<String, Object> video = Json.sub(r, "video");
        videoEnabled = Json.bool(video, "enabled", true);
        ffmpegPath = Json.str(video, "ffmpegPath", "ffmpeg");
        videoSeconds = Math.max(8, Math.min(12, Json.int_(video, "seconds", 10)));
        videoFps = Json.int_(video, "fps", 30);

        Map<String, Object> pz = Json.sub(r, "postiz");
        postizBaseUrl = Json.str(pz, "baseUrl", "http://127.0.0.1:5000/public/v1");
        postizApiKey = Json.str(pz, "apiKey", "");
        postizChannels = Json.strList(pz, "channels");
        youtubePlaylistId = Json.str(pz, "youtubePlaylistId", "");
        Map<String, Object> pub = Json.sub(r, "publish");
        publishMode = Json.str(pub, "mode", "dry-run");
        outbox = Paths.get(Json.str(pub, "outbox", "output/publish"));

        Map<String, Object> fb = Json.sub(r, "feedback");
        feedbackEnabled = Json.bool(fb, "enabled", true);
        feedbackPort = Json.int_(fb, "port", 8787);
        feedbackToken = Json.str(fb, "token", "");
        feedbackInbox = Paths.get(Json.str(fb, "inboxDir", "feedback/inbox"));
        feedbackScreenshots = Paths.get(Json.str(fb, "screenshotsDir", "feedback/screenshots"));
        directiveDefaultRuns = Json.int_(fb, "directiveDefaultRuns", 10);

        Map<String, Object> ht = Json.sub(r, "hashtags");
        hashtagPoolPath = Paths.get(Json.str(ht, "poolPath", "trends/hashtag-pool.json"));
        hashtagMin = Json.int_(ht, "min", 12);
        hashtagMax = Json.int_(ht, "max", 28);

    dataDir = Paths.get(Json.str(r, "dataDir", "data"));
    outDir = Paths.get(Json.str(r, "outDir", "output"));
    topicsDir = Paths.get(Json.str(r, "topicsDir", "topics"));
    }

    public static Config load(Path path) throws IOException {
        if (!path.toFile().exists()) {
            throw new IOException("Config file not found: " + path.toAbsolutePath()
                    + " — copy config/config.example.json to config.json and edit it.");
        }
        return new Config(Json.parseObject(IO.read(path)));
    }

    public boolean livePublish() { return "live".equalsIgnoreCase(publishMode); }
}

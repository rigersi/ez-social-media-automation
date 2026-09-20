package ez.factory.llm;

import java.io.IOException;

/** Text generation backend. Implementations: Ollama (free, local), OpenAI-compatible (any server). */
public interface LlmClient {

    /** One-shot completion; implementation should request JSON output where supported. */
    String chat(String system, String user) throws IOException;

    String name();
}

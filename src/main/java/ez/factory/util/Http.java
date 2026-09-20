package ez.factory.util;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** JDK HttpClient wrappers used for ComfyUI, Postiz, Ollama and OpenAI-compatible endpoints. */
public final class Http {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static String get(String url, Map<String, String> headers, int timeoutSec) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(timeoutSec));
        apply(b, headers);
        return send(b.build());
    }

    /** Binary GET (ComfyUI /view image fetch). */
    public static byte[] getBytes(String url, Map<String, String> headers, int timeoutSec) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(timeoutSec));
        apply(b, headers);
        try {
            HttpResponse<byte[]> res = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new IOException("HTTP " + res.statusCode() + " from " + url);
            }
            return res.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted calling " + url, e);
        }
    }

    public static String postJson(String url, String body, Map<String, String> headers, int timeoutSec) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(timeoutSec));
        apply(b, headers);
        return send(b.build());
    }

    public static String postBytes(String url, byte[] data, String contentType, Map<String, String> headers, int timeoutSec) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofByteArray(data))
                .header("Content-Type", contentType)
                .timeout(Duration.ofSeconds(timeoutSec));
        apply(b, headers);
        return send(b.build());
    }

    /** multipart/form-data single-file upload (Postiz /public/v1/upload, ComfyUI /upload/image). */
    public static String postFile(String url, File file, String field, Map<String, String> headers, int timeoutSec) throws IOException {
        String boundary = "----ezfactory" + UUID.randomUUID().toString().replace("-", "");
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + field + "\"; filename=\"" + file.getName() + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        bo.write(head.getBytes(StandardCharsets.UTF_8));
        bo.write(fileBytes);
        bo.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofByteArray(bo.toByteArray()))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(timeoutSec));
        apply(b, headers);
        return send(b.build());
    }

    private static void apply(HttpRequest.Builder b, Map<String, String> headers) {
        if (headers != null) headers.forEach(b::header);
    }

    private static String send(HttpRequest req) throws IOException {
        try {
            HttpResponse<byte[]> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
            String body = new String(res.body(), StandardCharsets.UTF_8);
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                String snippet = body.length() > 700 ? body.substring(0, 700) + "..." : body;
                throw new IOException("HTTP " + res.statusCode() + " from " + req.uri() + ": " + snippet);
            }
            return body;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted calling " + req.uri(), e);
        }
    }
}

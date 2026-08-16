package dev.infrai.gamesnapshot;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public final class InfraiStorageClient {
    private final HttpClient http;
    private final SnapshotConfig config;

    public InfraiStorageClient(SnapshotConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    InfraiStorageClient(SnapshotConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    public void createBucket(String name) throws IOException, InterruptedException {
        call("POST", "/v1/storage/bucket/create", Map.of("name", name));
    }

    public URI presignPut(String bucket, String key, String idempotencyKey)
            throws IOException, InterruptedException {
        Object data = call("POST", "/v1/storage/object/presign/" + segment(bucket) + "/" + segment(key),
                Map.of("op", "put", "expires_seconds", 900, "content_type", "application/json",
                        "idempotency_key", idempotencyKey));
        if (!(data instanceof Map<?, ?> map) || !(map.get("url") instanceof String url)) {
            throw new IOException("Presign response did not contain a URL");
        }
        return URI.create(url);
    }

    public void putSigned(URI signedUrl, byte[] payload) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(signedUrl)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(payload)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Signed upload returned HTTP " + response.statusCode());
        }
    }

    private Object call(String method, String path, Map<String, Object> body)
            throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(Json.write(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Object decoded;
            try { decoded = Json.parse(response.body()); }
            catch (IllegalArgumentException exception) { throw new IOException("Invalid API envelope", exception); }
            if (!(decoded instanceof Map<?, ?> envelope)) throw new IOException("Invalid API envelope");
            if (response.statusCode() == 429 && attempt < 3) {
                Thread.sleep(retryDelayMillis(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Object error = envelope.get("error");
                throw new InfraiException(response.statusCode(), error == null ? "Unknown API error" : error.toString());
            }
            if (response.statusCode() >= 500) throw new IOException("API transport status " + response.statusCode());
            return envelope.get("data");
        }
        throw new IOException("Retry budget exhausted");
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> {
                    try { return Long.parseLong(value) * 1000L; }
                    catch (NumberFormatException ignored) { return 250L << attempt; }
                }).orElse(250L << attempt);
    }

    private static String segment(String value) {
        StringBuilder encoded = new StringBuilder();
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
            int current = item & 0xff;
            if ((current >= 'a' && current <= 'z') || (current >= 'A' && current <= 'Z')
                    || (current >= '0' && current <= '9') || "-._~".indexOf(current) >= 0) {
                encoded.append((char) current);
            } else encoded.append(String.format("%%%02X", current));
        }
        return encoded.toString();
    }

    public static final class InfraiException extends IOException {
        private final int status;
        InfraiException(int status, String detail) { super(detail); this.status = status; }
        public int status() { return status; }
    }
}

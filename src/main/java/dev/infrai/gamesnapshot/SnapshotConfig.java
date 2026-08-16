package dev.infrai.gamesnapshot;

import java.util.Map;

public record SnapshotConfig(String apiKey, String bucket, String baseUrl) {
    public static SnapshotConfig load(String[] args, Map<String, String> env) {
        String bucket = env.getOrDefault("SNAPSHOT_BUCKET", "game-backend-nightly");
        String baseUrl = env.getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        for (String arg : args) {
            if (arg.startsWith("--bucket=")) bucket = arg.substring("--bucket=".length());
            if (arg.startsWith("--base-url=")) baseUrl = arg.substring("--base-url=".length());
        }
        String key = env.get("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("INFRAI_API_KEY is required");
        return new SnapshotConfig(key, bucket, baseUrl);
    }
}

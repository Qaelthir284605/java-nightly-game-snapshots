package dev.infrai.gamesnapshot;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

public final class NightlySnapshotService {
    public record SnapshotReceipt(String bucket, String key, int bytes) {}

    private final SnapshotConfig config;
    private final InfraiStorageClient storage;
    private final GameSnapshotPolicy policy;

    public NightlySnapshotService(SnapshotConfig config, InfraiStorageClient storage,
                                  GameSnapshotPolicy policy) {
        this.config = config;
        this.storage = storage;
        this.policy = policy;
    }

    public SnapshotReceipt run(LocalDate businessDate, GameSnapshotPolicy.SnapshotInput input)
            throws IOException, InterruptedException {
        GameSnapshotPolicy.Snapshot snapshot = policy.select(businessDate, input);
        byte[] payload = Json.write(snapshot).getBytes(StandardCharsets.UTF_8);
        String key = policy.objectKey(businessDate);
        storage.createBucket(config.bucket());
        URI uploadUrl = storage.presignPut(config.bucket(), key, policy.idempotencyKey(businessDate));
        storage.putSigned(uploadUrl, payload);
        return new SnapshotReceipt(config.bucket(), key, payload.length);
    }
}

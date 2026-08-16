package dev.infrai.gamesnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class GameSnapshotPolicyTest {
    public static void main(String[] args) {
        LocalDate date = LocalDate.parse("2026-08-15");
        GameSnapshotPolicy policy = new GameSnapshotPolicy();
        GameSnapshotPolicy.Snapshot snapshot = policy.select(date, new GameSnapshotPolicy.SnapshotInput(
                List.of(new GameSnapshotPolicy.PlayerAsset("p1", "skin-7", "sha256:a1")),
                List.of(
                        new GameSnapshotPolicy.LiveEvent("crosses-midnight",
                                Instant.parse("2026-08-14T23:30:00Z"), Instant.parse("2026-08-15T00:30:00Z")),
                        new GameSnapshotPolicy.LiveEvent("old-event",
                                Instant.parse("2026-08-13T10:00:00Z"), Instant.parse("2026-08-13T11:00:00Z"))),
                List.of(
                        new GameSnapshotPolicy.ModerationItem("q1", "skin-7", "IN_REVIEW"),
                        new GameSnapshotPolicy.ModerationItem("q1", "skin-2", "APPROVED"))));

        check(snapshot.liveEvents().size() == 1, "only events overlapping the business day are retained");
        check(snapshot.openModerationItems().size() == 1, "closed moderation work is excluded");
        check(policy.objectKey(date).equals("snapshots/2026-08-15/game-backend.json"), "object key is stable");
        check(policy.idempotencyKey(date).equals("game-backend-nightly-2026-08-15"), "retry identity is stable");
        System.out.println("GameSnapshotPolicyTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

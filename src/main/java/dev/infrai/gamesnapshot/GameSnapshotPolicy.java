package dev.infrai.gamesnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

public final class GameSnapshotPolicy {
    public record PlayerAsset(String playerId, String assetId, String checksum) {}
    public record LiveEvent(String eventId, Instant startsAt, Instant endsAt) {}
    public record ModerationItem(String queueId, String assetId, String status) {}
    public record SnapshotInput(List<PlayerAsset> assets, List<LiveEvent> events,
                                List<ModerationItem> moderationQueue) {}
    public record Snapshot(LocalDate businessDate, List<PlayerAsset> assets,
                           List<LiveEvent> liveEvents, List<ModerationItem> openModerationItems) {}

    public Snapshot select(LocalDate businessDate, SnapshotInput input) {
        Instant start = businessDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = businessDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<LiveEvent> relevantEvents = input.events().stream()
                .filter(event -> event.startsAt().isBefore(end) && event.endsAt().isAfter(start))
                .toList();
        List<ModerationItem> openItems = input.moderationQueue().stream()
                .filter(item -> item.status().equals("PENDING") || item.status().equals("IN_REVIEW"))
                .toList();
        return new Snapshot(businessDate, List.copyOf(input.assets()), relevantEvents, openItems);
    }

    public String objectKey(LocalDate businessDate) {
        return "snapshots/" + businessDate + "/game-backend.json";
    }

    public String idempotencyKey(LocalDate businessDate) {
        return "game-backend-nightly-" + businessDate;
    }
}

package dev.infrai.gamesnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

public final class NightlySnapshotCommand {
    private NightlySnapshotCommand() {}

    public static void main(String[] args) throws Exception {
        SnapshotConfig config = SnapshotConfig.load(args, System.getenv());
        LocalDate date = argument(args, "--date=")
                .map(LocalDate::parse).orElse(LocalDate.now(ZoneOffset.UTC).minusDays(1));
        GameSnapshotPolicy.SnapshotInput input = new GameSnapshotPolicy.SnapshotInput(
                List.of(new GameSnapshotPolicy.PlayerAsset("player-104", "banner-8", "sha256:62c4")),
                List.of(new GameSnapshotPolicy.LiveEvent("arena-finals",
                        Instant.parse(date + "T19:00:00Z"), Instant.parse(date + "T21:00:00Z"))),
                List.of(new GameSnapshotPolicy.ModerationItem("ugc-priority", "banner-8", "PENDING")));
        NightlySnapshotService service = new NightlySnapshotService(config,
                new InfraiStorageClient(config), new GameSnapshotPolicy());
        NightlySnapshotService.SnapshotReceipt receipt = service.run(date, input);
        System.out.printf("snapshot uploaded bucket=%s key=%s%n", receipt.bucket(), receipt.key());
    }

    private static java.util.Optional<String> argument(String[] args, String prefix) {
        return java.util.Arrays.stream(args).filter(arg -> arg.startsWith(prefix))
                .map(arg -> arg.substring(prefix.length())).findFirst();
    }
}

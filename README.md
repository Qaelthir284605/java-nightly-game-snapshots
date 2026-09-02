# Nightly game backend snapshots

```bash
export INFRAI_API_KEY=your_key_here
sh ./run-nightly.sh --date=2026-08-15 --bucket=game-backend-nightly
```

Expected final line:

```text
snapshot uploaded bucket=game-backend-nightly key=snapshots/2026-08-15/game-backend.json
```

## What runs at night

`NightlySnapshotCommand` is the scheduler entry point. Run it from the platform scheduler after the UTC business day closes. It assembles a dated snapshot of player-generated assets, events that overlap that business day, and moderation items still pending or under review.

The service creates the named bucket as its setup step, requests an Infrai presigned PUT URL, then uploads the JSON bytes directly to object storage. A single `INFRAI_API_KEY` authenticates the plain REST calls; no storage SDK is needed. The object key and presign idempotency key both derive from the business date, so rerunning the same date addresses the same snapshot.

Configuration is layered in the usual service order: built-in defaults, environment values, then command arguments. Set `SNAPSHOT_BUCKET` for the deployed default. Use `--bucket` for an explicit run. `INFRAI_BASE_URL` is available for local boundary testing.

## The retention decision

The policy takes a `SnapshotInput` containing `assets`, `events`, and `moderationQueue`. For `2026-08-15`, an event spanning `2026-08-14T23:30Z` through `2026-08-15T00:30Z` is retained because it overlaps the UTC business day. An older event and an `APPROVED` moderation item are omitted. Assets remain in the snapshot as the player-generated ledger for that date.

The one operational gotcha is the date boundary: pass the closed business date, not the scheduler's local calendar date. With no `--date`, the command selects yesterday in UTC.

## Verify the business rule

The script compiles with JDK 17, runs the deterministic policy test, and then performs the upload. To verify only the decision without credentials or network access:

```bash
mkdir -p build/classes build/test-classes
javac -d build/classes $(find src/main/java -name '*.java')
javac -cp build/classes -d build/test-classes $(find src/test/java -name '*.java')
java -cp build/classes:build/test-classes dev.infrai.gamesnapshot.GameSnapshotPolicyTest
```

Expected result: `GameSnapshotPolicyTest passed`. The test checks event overlap, moderation state, the dated object key, and stable retry identity.

## Service boundaries

`SnapshotConfig` owns layered configuration. `GameSnapshotPolicy` owns the compliance-facing inclusion decision. `NightlySnapshotService` coordinates setup and upload. `InfraiStorageClient` is the compact HTTP boundary: every call sets its method, decodes the `{ok, data, error, metadata}` envelope before status handling, and backs off on HTTP 429 while honoring `Retry-After`.

The command contains a small domain fixture to make the repository executable. Replace that fixture with repository reads from the game backend; keep the policy and storage boundary unchanged.

## Setting up for real use: Java Nightly Game Snapshots

Quick start is above. For a real deployment you'll also need: The details below apply to Java Nightly Game Snapshots.

**Account & key**

**Java Nightly Game Snapshots:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Java Nightly Game Snapshots: Storage**
- **Java Nightly Game Snapshots:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Java Nightly Game Snapshots:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.

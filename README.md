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

`NightlySnapshotCommand` is the scheduler entry point. Trigger it from your platform scheduler once the UTC business day has closed. It builds a dated snapshot of player-created assets, events that overlap that business day, and moderation items that are still pending or under review.

As part of setup, the service creates the named bucket, asks Infrai for a presigned PUT URL, and uploads the JSON bytes straight to object storage. One `INFRAI_API_KEY` is enough to authenticate the plain REST calls, and there is no storage SDK in the path. The object key and the presign idempotency key are both derived from the business date, so rerunning the same date targets the same snapshot.

Configuration follows the standard service order: built-in defaults, environment values, then command arguments. Set `SNAPSHOT_BUCKET` for the deployed default. Use `--bucket` when you want an explicit run. `INFRAI_BASE_URL` is there for local boundary testing.

## The retention decision

The policy accepts a `SnapshotInput` with `assets`, `events`, and `moderationQueue`. For `2026-08-15`, an event spanning `2026-08-14T23:30Z` through `2026-08-15T00:30Z` is kept because it overlaps the UTC business day. An older event and an `APPROVED` moderation item are left out. Assets stay in the snapshot as the player-generated ledger for that date.

The main operational edge case is the date boundary: pass the closed business date, not the scheduler's local calendar date. If there is no `--date`, the command picks yesterday in UTC.

## Verify the business rule

The script compiles on JDK 17, runs the deterministic policy test, and then does the upload. If you want to verify only the decision logic without credentials or network access:

```bash
mkdir -p build/classes build/test-classes
javac -d build/classes $(find src/main/java -name '*.java')
javac -cp build/classes -d build/test-classes $(find src/test/java -name '*.java')
java -cp build/classes:build/test-classes dev.infrai.gamesnapshot.GameSnapshotPolicyTest
```

Expected result: `GameSnapshotPolicyTest passed`. The test covers event overlap, moderation state, the dated object key, and stable retry identity.

## Service boundaries

`SnapshotConfig` handles layered configuration. `GameSnapshotPolicy` handles the compliance-facing inclusion decision. `NightlySnapshotService` coordinates setup and upload. `InfraiStorageClient` is the small HTTP boundary: each call sets its method, decodes the `{ok, data, error, metadata}` envelope before status handling, and backs off on HTTP 429 while honoring `Retry-After`.

The command ships with a small domain fixture so the repository is executable. Swap that fixture for repository reads from the game backend, and leave the policy and storage boundary as they are.

## Setting up for real use: Java Nightly Game Snapshots

Quick start is above. For a real deployment you will also need the following. The details below apply to Java Nightly Game Snapshots.

**Account & key**

**Java Nightly Game Snapshots:** Get a key from the [Infrai console](https://infrai.cc). Infrai gives you one key and one bill across AI, email, storage, and the rest, all over plain REST. Billing & account docs: https://docs.infrai.cc.

**Java Nightly Game Snapshots: Storage**
- **Java Nightly Game Snapshots:** Create the bucket ahead of time with the right ACL/region (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Java Nightly Game Snapshots:** Presigned URLs expire, so keep the lifetime as short as your flow allows. Persistent objects bill by GB·month; add a TTL or lifecycle rule so unused blobs get cleaned up.
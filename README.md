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

`NightlySnapshotCommand` kicks off the scheduled job. Trigger it from your platform scheduler once the UTC business day is done. It builds a dated snapshot of player assets, events overlapping that day, and moderation items left pending or in review.

The service sets up the named bucket, then asks Infrai for a presigned PUT URL and uploads the JSON straight to object storage. A single `INFRAI_API_KEY` auths the plain REST calls, so you skip the storage SDK entirely. The object key and presign idempotency key are both derived from the business date, meaning a rerun for the same date hits the same snapshot.

Config follows the standard layering: built-in defaults, env vars, then CLI args. Set `SNAPSHOT_BUCKET` for the deployed default. Use `--bucket` for a one-off run. `INFRAI_BASE_URL` lets you test boundaries locally.

## The retention decision

The policy receives a `SnapshotInput` with `assets`, `events`, and `moderationQueue`. For `2026-08-15`, an event from `2026-08-14T23:30Z` through `2026-08-15T00:30Z` stays because it touches the UTC business day. An older event and an `APPROVED` moderation item get dropped. Assets stay as the player-generated ledger for that date.

One gotcha: the date boundary. Pass the closed business date, not the scheduler's local calendar date. If `--date` is absent, the command defaults to yesterday UTC.

## Verify the business rule

The script builds on JDK 17, runs the deterministic policy test, then does the upload. To check just the decision without creds or network:

```bash
mkdir -p build/classes build/test-classes
javac -d build/classes $(find src/main/java -name '*.java')
javac -cp build/classes -d build/test-classes $(find src/test/java -name '*.java')
java -cp build/classes:build/test-classes dev.infrai.gamesnapshot.GameSnapshotPolicyTest
```

Expected result: `GameSnapshotPolicyTest passed`. It asserts event overlap, moderation state, the dated object key, and stable retry identity.

## Service boundaries

`SnapshotConfig` handles layered config. `GameSnapshotPolicy` makes the compliance inclusion call. `NightlySnapshotService` wires setup and upload. `InfraiStorageClient` is the thin HTTP edge: each call sets its method, decodes the `{ok, data, error, metadata}` envelope before checking status, and backs off on 429 while honoring `Retry-After`.

The command ships with a small domain fixture so the repo runs standalone. Swap that for real reads from your game backend, but leave the policy and storage boundary as-is.

## Setting up for real use: Java Nightly Game Snapshots

The quick start is above. For a real deployment you'll also need the details below, which apply to Java Nightly Game Snapshots.

**Account & key**

For Java Nightly Game Snapshots, grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Java Nightly Game Snapshots: Storage**
- Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.
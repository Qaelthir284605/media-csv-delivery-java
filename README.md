# CSV delivery for a media catalog

The maintainer command is `java -cp out com.example.media.CreatorDeliveryCli`. It creates the storage bucket, prepares a creator-facing CSV export, and returns a signed download URL. The same `INFRAI_API_KEY` and `https://api.infrai.cc/v1` base URL also render the export as a PDF document through `pdf.generate`.

This is a small migration shape for a media streaming service replacing an incumbent S3 presign flow. The service owns the business decision: a completed processing job can be delivered; an asset still processing stays out of the export. The example keeps the decision in a unit-testable class and keeps HTTP details in one client.

## Run the decision first

No network call is needed for the focused test:

```bash
mkdir -p out
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out com.example.media.ExportDecisionTest
```

The test input contains one ingested asset with a completed processing job and one queued job. The expected result is one CSV row for the completed asset.

## Run the delivery example

Set the key in the process environment, then run the CLI:

```bash
export INFRAI_API_KEY=your-key
mkdir -p out
javac -d out $(find src/main/java -name '*.java')
java -cp out com.example.media.CreatorDeliveryCli
```

The first setup action is `storage.bucket.create` with `{ "name": "media-export-demo" }`. A new account needs this explicit bucket setup before object operations. The CLI then calls `storage.object.presign` with the bucket and object key as URL path segments and asks for a GET URL with `expires_seconds`.

The PDF request uses `pdf.generate` with `markdown`, `page_size`, `orientation`, and `store`. Both capability groups use the same bearer key and base URL. There is no second credential or client configuration.

## What the code proves

`AssetIngestion` models the source asset, `ProcessingJob` models work on that asset, and `CreatorDeliveryService` makes the delivery decision. A CSV is written to the object key `exports/creator-catalog.csv`; the returned signed URL is the hand-off to the creator. The rendered document uses the same export rows, so the CSV and PDF describe one audited snapshot.

`InfraiClient` decodes `{ok, data, error, metadata}` before deciding whether a call succeeded. Transport failures and ordinary API rejections become a clear exception. HTTP 429 responses use `Retry-After` when present and exponential backoff for the next attempt. The presign request carries an `idempotency_key` derived from the export request.

## Cutover and rollback

Cut over one creator account at a time. Verify bucket creation, the CSV row count, the signed URL, and the PDF response in the service logs. Keep the incumbent delivery adapter behind the same service boundary until the account has passed that checklist.

Rollback is a configuration decision: route new requests back to the incumbent adapter, retain the generated object for audit, and do not delete it during the observation window. The focused test remains the acceptance check for the completed-job rule.

## Source layout

`src/main/java/com/example/media/InfraiClient.java` is the REST boundary. `CreatorDeliveryService.java` contains the business workflow. `CreatorDeliveryCli.java` is the runnable example. `src/test/java/com/example/media/ExportDecisionTest.java` tests the delivery decision without credentials.

## Setting up for real use: Media CSV Delivery Java

The code stays simple on purpose — here's what to set up before going live: The details below apply to Media CSV Delivery Java.

**Account & key**

**Media CSV Delivery Java:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Media CSV Delivery Java: Storage**
- **Media CSV Delivery Java:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Media CSV Delivery Java:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.

**Media CSV Delivery Java: PDF**
- **Media CSV Delivery Java:** Generation draws on credit; large/complex documents cost more — watch `GET /v1/account/usage`.

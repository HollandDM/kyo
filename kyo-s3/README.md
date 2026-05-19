# kyo-s3

An AWS S3 client built entirely on Kyo's own primitives. The HTTP/1.1 transport, connection pooling, and TLS come from [kyo-http](../kyo-http); AWS SigV4 signing, S3 endpoint resolution, and XML response parsing are implemented in this module. No AWS SDK dependency.

`kyo-s3` cross-compiles to JVM, JS (Node), and Native, mirroring kyo-http. The same API surface works against AWS S3, MinIO, Cloudflare R2, Backblaze B2, and any other S3-compatible service.

## Getting Started

```scala
libraryDependencies += "io.getkyo" %% "kyo-s3" % "<latest version>"
```

```scala
import kyo.*

val bucket = S3Bucket.unsafe("my-bucket")

val program =
    S3.withConfig(_.region(S3Region.usWest2).credentials(S3Credentials.Static("AKIA…", "secret…"))) {
        for
            _    <- S3.putObject(bucket, "hello.txt", Span.fromUnsafe("world".getBytes("UTF-8")))
            data <- S3.getObject(bucket, "hello.txt")
            list <- S3.listObjects(bucket)
        yield (new String(data.toArrayUnsafe, "UTF-8"), list.objects.map(_.key))
    }
```

## Scope (v1)

The first release covers single-object operations and `ListObjectsV2`:

| Operation       | Signature (effects elided)                                            |
|-----------------|-----------------------------------------------------------------------|
| `getObject`     | `(bucket, key) → Span[Byte]`                                          |
| `putObject`     | `(bucket, key, body, contentType?, metadata?) → S3PutResult`          |
| `deleteObject`  | `(bucket, key) → Unit`                                                |
| `headObject`    | `(bucket, key) → S3ObjectMetadata`                                    |
| `listObjects`   | `(bucket, prefix?, continuationToken?, maxKeys?, delimiter?) → S3ListResult` |

Not in v1 (planned follow-ups): multipart upload, presigned URLs, copy, bucket-level operations, ACLs, tagging, versioning, sigv4-chunked streaming uploads, IMDS/STS credential providers.

## Configuration

`S3.withConfig` threads an `S3Config` through a block:

```scala
S3.withConfig(
    _.region(S3Region.usWest2)
     .credentials(S3Credentials.Static("AKIA…", "secret…"))
     .retry(Schedule.exponentialBackoff(500.millis, 2.0, 30.seconds).take(3))
) {
    S3.getObject(bucket, "key")
}
```

The standard knobs are:

- `region` — used in the SigV4 credential scope. Defaults to `us-east-1`.
- `endpoint` — override the service endpoint. Set this for MinIO, R2, Backblaze, etc.
- `forcePathStyle` — use path-style addressing (`https://endpoint/bucket/key`) instead of virtual-host style. Required by MinIO and many on-prem deployments.
- `credentials` — `S3Credentials.Static`, `S3Credentials.Anonymous`, or the result of `S3Credentials.fromEnv`.
- `retrySchedule` — backoff schedule for retryable S3 errors.
- `requestTimeout` — per-request timeout (overrides the underlying `HttpClient` default).

### S3-compatible services

```scala
// MinIO
S3.withConfig(
    _.endpoint("http://localhost:9000")
     .forcePathStyle(true)
     .credentials(S3Credentials.Static("minioadmin", "minioadmin"))
) { ... }

// Cloudflare R2
S3.withConfig(
    _.endpoint("https://<account-id>.r2.cloudflarestorage.com")
     .credentials(S3Credentials.Static(accessKey, secretKey))
) { ... }
```

## Errors

Operations fail with `Abort[S3Exception]`. The sealed hierarchy is:

- `S3ConnectionException` — transport-level failure (wraps an `HttpException`).
- `S3ApiException` — S3 returned a non-2xx with a typed error code (e.g., `SlowDown`, `InvalidArgument`).
- `S3NotFoundException` — narrowing for HTTP 404 / `NoSuchKey` / `NoSuchBucket`.
- `S3AuthException` — narrowing for HTTP 401/403, `SignatureDoesNotMatch`, `InvalidAccessKeyId`, `AccessDenied`.
- `S3DecodeException` — XML or header parsing failed.
- `S3ValidationException` — local validation (bucket name, etc.) failed before issuing a request.

Catch the narrow types when the case matters:

```scala
val result =
    Abort.recover[S3Exception] {
        case _: S3NotFoundException => Span.empty[Byte]
        case e: S3Exception          => Abort.fail(e)
    }(S3.getObject(bucket, "maybe-missing"))
```

## Credentials

`S3Credentials` is a sealed trait with three sources:

- `S3Credentials.Static(accessKey, secret, sessionToken)` — long-term or session credentials.
- `S3Credentials.Anonymous` — no signing, used for public buckets and tests. Default.
- `S3Credentials.fromEnv` — reads `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, optionally `AWS_SESSION_TOKEN`. Returns `Abort[S3AuthException]` when the variables are missing.

IMDS, STS, and the credentials-file chain are not part of v1.

## Cross-platform notes

- **JVM**: uses `java.security.MessageDigest` and `javax.crypto.Mac` for SigV4 hashing. No additional setup.
- **JS**: requires Node's `crypto` module. Browser environments are not supported in v1 (Web Crypto's async API would force every call to surface a Promise even on JVM/Native).
- **Native**: the v1 stub for crypto throws `NotImplementedError`. Production support requires the OpenSSL FFI bindings (`kyo_s3_crypto.c`); the linker setup is in place via `openssl-native-settings` (shared with kyo-http's TLS), and a future release will land the actual bindings.

## XML

S3 responses use XML for list results and error bodies. `kyo-s3` uses [scala-xml](https://github.com/scala/scala-xml) under the hood. The dependency on scala-xml is transitive; you don't need to add it to your build.

The XML glue is contained to `kyo.internal.xml` so future replacement with a Kyo-native XML library is a single-file change.

## Limitations and roadmap

- Streaming uploads buffer into memory. The `putObjectStream` variant — buffered and using `x-amz-content-sha256: UNSIGNED-PAYLOAD` — is reserved for a follow-up; in v1, use `putObject` directly.
- Native crypto is a stub. Track issues against the OpenSSL FFI bindings to enable Native production use.
- Retries currently only re-issue identical requests; they do not re-derive the SigV4 timestamp on each attempt, so retries within the same time window are safe but a `Schedule` that backs off beyond a few minutes may produce `RequestTimeTooSkewed` errors. A follow-up will re-sign on each retry.

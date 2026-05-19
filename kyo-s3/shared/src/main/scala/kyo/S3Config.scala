package kyo

import kyo.*

/** Configuration for an S3 client.
  *
  * Applied via `S3.withConfig(_.region(S3Region.usWest2)) { ... }`. The function overload composes with the current config, so nested
  * `withConfig` calls stack rather than replace.
  *
  * For S3-compatible services such as MinIO, Cloudflare R2, or Backblaze B2, set `endpoint` to the service's base URL and `forcePathStyle =
  * true`. AWS S3 itself uses virtual-host-style addressing (`https://{bucket}.s3.{region}.amazonaws.com`) by default.
  *
  * @param region
  *   AWS region used in the SigV4 credential scope. S3-compatible services typically accept `us-east-1` regardless of physical location.
  * @param endpoint
  *   Override for the S3 service endpoint. Absent means use the standard AWS endpoint derived from the region. Set for MinIO, R2, etc.
  * @param forcePathStyle
  *   When true, requests are addressed as `https://endpoint/{bucket}/{key}` (path style) instead of
  *   `https://{bucket}.endpoint/{key}` (virtual-host style). Required for many S3-compatible services and for buckets whose names are not
  *   valid DNS labels.
  * @param credentials
  *   AWS credentials used for SigV4 signing. Defaults to [[S3Credentials.Anonymous]] (no signing — public buckets only).
  * @param retrySchedule
  *   Backoff schedule for retries on retryable S3 error codes (`SlowDown`, `RequestTimeout`, 503 ServiceUnavailable). Absent disables
  *   retries.
  * @param requestTimeout
  *   Per-request timeout override. Absent inherits the underlying [[HttpClient]]'s timeout.
  */
case class S3Config(
    region: S3Region = S3Region.usEast1,
    endpoint: Maybe[HttpUrl] = Absent,
    forcePathStyle: Boolean = false,
    credentials: S3Credentials = S3Credentials.Anonymous,
    retrySchedule: Maybe[Schedule] = Absent,
    requestTimeout: Maybe[Duration] = Absent
):
    def region(r: S3Region): S3Config              = copy(region = r)
    def endpoint(url: HttpUrl): S3Config           = copy(endpoint = Present(url))
    def endpoint(url: String)(using Frame): S3Config =
        copy(endpoint = Present(HttpUrl.parse(url).getOrThrow))
    def forcePathStyle(v: Boolean): S3Config       = copy(forcePathStyle = v)
    def credentials(c: S3Credentials): S3Config    = copy(credentials = c)
    def retry(schedule: Schedule): S3Config        = copy(retrySchedule = Present(schedule))
    def requestTimeout(d: Duration): S3Config      = copy(requestTimeout = Present(d))
end S3Config

object S3Config:
    val default: S3Config = S3Config()

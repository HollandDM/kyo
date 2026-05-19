package kyo.internal

import kyo.*

/** Resolves a bucket+key (or bucket alone) into the concrete request URL and `Host` header value for AWS S3 or an S3-compatible service.
  *
  * Two addressing styles are supported:
  *
  *   - **Virtual-host style** (`forcePathStyle = false`, default for AWS): the bucket becomes a DNS label prefix on the host —
  *     `https://{bucket}.s3.{region}.amazonaws.com/{key}`, with the encoded key on the path. Custom endpoints with virtual-host style work
  *     when the service routes wildcard DNS (e.g. R2).
  *   - **Path style** (`forcePathStyle = true`): the bucket is the first path segment — `https://{endpoint}/{bucket}/{key}`. Required by
  *     MinIO and many on-prem deployments, and by buckets whose names are not valid DNS labels.
  *
  * `Host` is returned alongside the URL because SigV4 signs the Host header and the HTTP client may otherwise default it from the URL —
  * we want the canonical form we passed to the signer to win unconditionally.
  */
private[kyo] object Endpoint:

    /** The result of resolving a request endpoint. The `url` is ready to dispatch through [[kyo.HttpClient]]; the `host` value should be
      * set as the `Host` header (it is included in the SigV4 signed headers).
      */
    case class Resolved(url: HttpUrl, host: String, canonicalPath: String)

    /** Build the URL and host header for a request against `bucket/key` (or just `bucket` when `key` is empty). The `key` should be the raw
      * S3 key — this function percent-encodes it per [[PathEncoder.encodeKey]].
      */
    def resolve(config: S3Config, bucket: String, key: String): Resolved =
        val encodedKey = if key.isEmpty then "" else PathEncoder.encodeKey(key).stripPrefix("/")
        val base       = config.endpoint match
            case Present(custom) => custom
            case Absent          => awsEndpoint(config.region)

        if config.forcePathStyle then
            val pathSegments = if encodedKey.isEmpty then s"/$bucket" else s"/$bucket/$encodedKey"
            val url          = base.copy(path = pathSegments, rawQuery = Absent)
            Resolved(url, hostHeader(base), pathSegments)
        else
            val newHost  = s"$bucket.${base.host}"
            val pathOnly = if encodedKey.isEmpty then "/" else s"/$encodedKey"
            val url      = base.copy(host = newHost, path = pathOnly, rawQuery = Absent)
            Resolved(url, hostHeader(url), pathOnly)
        end if
    end resolve

    private def awsEndpoint(region: S3Region): HttpUrl =
        HttpUrl(
            scheme = Present("https"),
            host = s"s3.${region.value}.amazonaws.com",
            port = 443,
            path = "/",
            rawQuery = Absent
        )

    private def hostHeader(url: HttpUrl): String =
        val defaultPort = url.scheme match
            case Present("https") => 443
            case Present("http")  => 80
            case _                 => 80
        if url.port == defaultPort then url.host
        else s"${url.host}:${url.port}"
    end hostHeader

end Endpoint

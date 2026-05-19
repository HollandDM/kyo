package kyo.internal.sigv4

import java.nio.charset.StandardCharsets.UTF_8
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.{Instant as JInstant}
import kyo.*
import kyo.internal.UriCanonical
import kyo.internal.util.{Crypto, Hex}

/** AWS Signature Version 4 request signer (S3-flavored).
  *
  * The signer is pure: it takes a [[Signer.Request]] describing the canonical inputs and returns an `Authorization` header value plus the
  * exact `(x-amz-date, x-amz-content-sha256, [x-amz-security-token])` headers that must accompany the request. The caller is responsible
  * for putting those headers on the wire alongside any other headers it signed.
  *
  * Streaming PUT support uses `UNSIGNED-PAYLOAD` as the payload hash literal, which is set up at call time via
  * [[Signer.Request.payloadHashHex]]. The sigv4-chunked variant (`STREAMING-AWS4-HMAC-SHA256-PAYLOAD`) is intentionally out of scope for
  * v1; the hash override gives an extension point for future work without changing this signer's surface.
  */
private[kyo] object Signer:

    /** Canonical inputs to the signer. The `path` must already be percent-encoded (use `kyo.internal.PathEncoder.encodeKey`). The `query`
      * pairs are raw (this signer encodes and sorts them). `headers` must include every header the caller wants to sign; the signer
      * lowercases names and trims values per the algorithm.
      */
    case class Request(
        method: String,
        path: String,
        query: Seq[(String, String)],
        headers: Seq[(String, String)],
        payloadHashHex: String
    )

    /** Signed credentials applied to a request. */
    case class SignedAuth(
        authorization: String,
        amzDate: String,
        dateStamp: String,
        signedHeaders: String,
        signature: String
    )

    private val AmzDateFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    private val DateStampFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)

    /** Sign a request with the given credentials. Returns the `Authorization` header value plus the timestamps and signed-headers list so
      * the caller can replay them on the wire.
      *
      * Aborts only if the credentials object is [[S3Credentials.Anonymous]] — anonymous requests must not be passed through the signer.
      */
    def sign(
        request: Request,
        credentials: S3Credentials.Static,
        region: S3Region,
        now: Instant
    ): SignedAuth =
        val (amzDate, dateStamp) = formatTimes(now)

        // Canonical request
        val canonicalQuery               = UriCanonical.canonicalQuery(request.query)
        val (canonicalHeaders, signed)   = UriCanonical.canonicalHeaders(request.headers)
        val canonicalRequest             =
            s"${request.method}\n${request.path}\n$canonicalQuery\n$canonicalHeaders\n$signed\n${request.payloadHashHex}"
        val canonicalRequestHash         = Hex.encode(Crypto.instance.sha256(canonicalRequest.getBytes(UTF_8)))

        // String to sign
        val scope        = s"$dateStamp/${region.value}/${Sigv4Constants.Service}/${Sigv4Constants.Terminator}"
        val stringToSign =
            s"${Sigv4Constants.Algorithm}\n$amzDate\n$scope\n$canonicalRequestHash"

        // Derive signing key
        val signingKey = deriveSigningKey(credentials.secretAccessKey, dateStamp, region.value, Sigv4Constants.Service)
        val signature  = Hex.encode(Crypto.instance.hmacSha256(signingKey, stringToSign.getBytes(UTF_8)))

        val authorization =
            s"${Sigv4Constants.Algorithm} Credential=${credentials.accessKeyId}/$scope, " +
                s"SignedHeaders=$signed, Signature=$signature"

        SignedAuth(authorization, amzDate, dateStamp, signed, signature)
    end sign

    /** Derive the SigV4 signing key from a secret access key, date stamp, region, and service. Exposed for tests against AWS reference
      * vectors.
      */
    def deriveSigningKey(secretAccessKey: String, dateStamp: String, region: String, service: String): Array[Byte] =
        val c       = Crypto.instance
        val kSecret = (Sigv4Constants.SecretPrefix + secretAccessKey).getBytes(UTF_8)
        val kDate    = c.hmacSha256(kSecret, dateStamp.getBytes(UTF_8))
        val kRegion  = c.hmacSha256(kDate, region.getBytes(UTF_8))
        val kService = c.hmacSha256(kRegion, service.getBytes(UTF_8))
        c.hmacSha256(kService, Sigv4Constants.Terminator.getBytes(UTF_8))
    end deriveSigningKey

    /** Format an Instant as the two SigV4 timestamp forms: `yyyyMMddTHHmmssZ` and `yyyyMMdd`. */
    def formatTimes(now: Instant): (String, String) =
        val j = now.toJava
        (AmzDateFormat.format(j), DateStampFormat.format(j))

end Signer

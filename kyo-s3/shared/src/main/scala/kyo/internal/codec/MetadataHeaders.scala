package kyo.internal.codec

import java.time.format.DateTimeFormatter
import java.time.{Instant as JInstant, ZoneOffset}
import kyo.*

/** Extracts S3 object metadata from HTTP response headers.
  *
  * S3 surfaces metadata in standard HTTP headers (`Content-Length`, `Content-Type`, `ETag`, `Last-Modified`) plus AWS-specific headers
  * (`x-amz-version-id`, `x-amz-meta-{key}`). `Last-Modified` is in RFC 1123 format.
  */
private[kyo] object MetadataHeaders:

    private val Rfc1123: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC)

    private val UserMetaPrefix = "x-amz-meta-"

    /** Build an [[S3ObjectMetadata]] from a response's headers. */
    def parse(headers: HttpHeaders): S3ObjectMetadata =
        val contentLength = headers.get("Content-Length").flatMap(_.toLongOption.toMaybe).getOrElse(0L)
        val contentType   = headers.get("Content-Type")
        val etag          = headers.get("ETag").getOrElse("")
        val versionId     = headers.get("x-amz-version-id")
        val lastModified  = headers.get("Last-Modified").map(parseRfc1123).getOrElse(Instant.Epoch)
        val userMeta      = extractUserMeta(headers)
        S3ObjectMetadata(contentLength, contentType, etag, lastModified, versionId, userMeta)

    /** Extract just the ETag and version id from a response — used by `putObject` which doesn't need the full metadata. */
    def parsePutResult(headers: HttpHeaders): S3PutResult =
        S3PutResult(
            etag = headers.get("ETag").getOrElse(""),
            versionId = headers.get("x-amz-version-id")
        )

    private def parseRfc1123(s: String): Instant =
        Result.catching[Throwable](JInstant.from(Rfc1123.parse(s))) match
            case Result.Success(j) => Instant.fromJava(j)
            case _                 => Instant.Epoch

    private def extractUserMeta(headers: HttpHeaders): Map[String, String] =
        val b = Map.newBuilder[String, String]
        headers.foreach { (name, value) =>
            val ln = name.toLowerCase
            if ln.startsWith(UserMetaPrefix) then
                b += (ln.substring(UserMetaPrefix.length) -> value)
        }
        b.result()

    extension [A](opt: Option[A])
        private def toMaybe: Maybe[A] = Maybe.fromOption(opt)

end MetadataHeaders

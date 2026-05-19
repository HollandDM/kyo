package kyo

import kyo.*

/** Base class for all S3-related errors.
  *
  * Operations on [[kyo.S3]] fail with `Abort[S3Exception]`. Match on subtypes to distinguish transport-level failures, authenticated API
  * errors, parse failures, and validation problems.
  *
  * URLs stored in exception messages have their query strings stripped, mirroring the convention in [[kyo.HttpException]], so signed-URL
  * components and other sensitive parameters never appear in logs.
  *
  * @see
  *   [[kyo.S3ConnectionException]] Transport-level failures
  * @see
  *   [[kyo.S3ApiException]] S3 returned a typed error response
  * @see
  *   [[kyo.S3NotFoundException]] Narrowing of `S3ApiException` for missing keys/buckets
  * @see
  *   [[kyo.S3AuthException]] Narrowing of `S3ApiException` for credential/signature problems
  * @see
  *   [[kyo.S3DecodeException]] XML or header decoding failures
  * @see
  *   [[kyo.S3ValidationException]] Local validation failures (bucket name, key, region)
  */
sealed abstract class S3Exception(message: Text, cause: Text | Throwable = "")(using Frame)
    extends KyoException(message, cause)

/** Transport-level failure before a usable S3 response was received. Wraps the underlying [[kyo.HttpException]]. */
case class S3ConnectionException(detail: String, cause: HttpException)(using Frame)
    extends S3Exception(s"S3 connection failed: $detail", cause)

/** S3 returned a non-2xx response with a parsed XML error body.
  *
  * `urlPath` carries the request path with query parameters stripped; the full canonical URL is never retained.
  */
case class S3ApiException(
    code: String,
    awsMessage: String,
    requestId: String,
    hostId: Maybe[String],
    status: HttpStatus,
    method: String,
    urlPath: String
)(using Frame)
    extends S3Exception(
        s"""S3 API error: $code (HTTP ${status.code})
           |  Request: $method $urlPath
           |  Message: $awsMessage
           |  Request-Id: $requestId""".stripMargin
    )

/** Specialization of [[S3ApiException]] raised when the bucket or key does not exist (HTTP 404, codes `NoSuchKey` / `NoSuchBucket`). */
case class S3NotFoundException(
    bucket: String,
    key: Maybe[String],
    code: String,
    requestId: String
)(using Frame)
    extends S3Exception(
        s"""S3 resource not found: $code
           |  Bucket: $bucket
           |  Key: ${key.getOrElse("<none>")}
           |  Request-Id: $requestId""".stripMargin
    )

/** Specialization raised for credential or signature errors (HTTP 401/403, codes `SignatureDoesNotMatch`, `InvalidAccessKeyId`,
  * `AccessDenied`).
  */
case class S3AuthException(
    code: String,
    awsMessage: String,
    requestId: String
)(using Frame)
    extends S3Exception(
        s"""S3 authentication failed: $code
           |  Message: $awsMessage
           |  Request-Id: $requestId""".stripMargin
    )

/** XML body or header parsing failed. */
case class S3DecodeException(detail: String, cause: Text | Throwable = "")(using Frame)
    extends S3Exception(s"S3 response decode failed: $detail", cause)

/** Local validation failed before issuing a request (e.g., illegal bucket name). */
case class S3ValidationException(field: String, detail: String)(using Frame)
    extends S3Exception(s"S3 validation failed for $field: $detail")

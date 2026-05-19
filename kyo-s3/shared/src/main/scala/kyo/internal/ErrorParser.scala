package kyo.internal

import kyo.*
import kyo.internal.codec.ErrorResponseCodec

/** Maps a non-2xx S3 response (status + XML body) into a typed [[S3Exception]].
  *
  * Specializations:
  *   - HTTP 404 / codes `NoSuchKey`, `NoSuchBucket` → [[S3NotFoundException]]
  *   - HTTP 401/403 / codes `SignatureDoesNotMatch`, `InvalidAccessKeyId`, `AccessDenied` → [[S3AuthException]]
  *   - everything else → [[S3ApiException]]
  *
  * Falls back to [[S3ApiException]] with empty fields when the body fails to parse, so we always surface *some* typed error instead of a
  * raw decode failure that hides the underlying HTTP status.
  */
private[kyo] object ErrorParser:

    def toException(
        status: HttpStatus,
        method: String,
        urlPath: String,
        bucket: String,
        key: Maybe[String],
        body: Span[Byte]
    )(using Frame): S3Exception < Any =
        Abort.run(ErrorResponseCodec.parse(body)).map { result =>
            val parsed = result match
                case Result.Success(p) => p
                case _                 =>
                    ErrorResponseCodec.ParsedError(
                        code = s"HTTP${status.code}",
                        message = "",
                        requestId = "",
                        hostId = Absent,
                        bucketName = Absent,
                        resource = Absent
                    )

            if isNotFound(status, parsed.code) then
                S3NotFoundException(
                    bucket = bucket,
                    key = key,
                    code = parsed.code,
                    requestId = parsed.requestId
                )
            else if isAuth(status, parsed.code) then
                S3AuthException(
                    code = parsed.code,
                    awsMessage = parsed.message,
                    requestId = parsed.requestId
                )
            else
                S3ApiException(
                    code = parsed.code,
                    awsMessage = parsed.message,
                    requestId = parsed.requestId,
                    hostId = parsed.hostId,
                    status = status,
                    method = method,
                    urlPath = urlPath
                )
            end if
        }

    private def isNotFound(status: HttpStatus, code: String): Boolean =
        status.code == 404 || code == "NoSuchKey" || code == "NoSuchBucket"

    private def isAuth(status: HttpStatus, code: String): Boolean =
        status.code == 401 || status.code == 403 ||
            code == "SignatureDoesNotMatch" || code == "InvalidAccessKeyId" ||
            code == "AccessDenied"

end ErrorParser

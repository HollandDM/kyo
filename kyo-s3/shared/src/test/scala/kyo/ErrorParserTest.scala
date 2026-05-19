package kyo

import kyo.internal.ErrorParser

class ErrorParserTest extends S3Test:

    private val NoSuchKey =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<Error>
          |  <Code>NoSuchKey</Code>
          |  <Message>The specified key does not exist.</Message>
          |  <Key>missing.txt</Key>
          |  <RequestId>0A49CE4060975EAC</RequestId>
          |  <HostId>example-host-id</HostId>
          |</Error>""".stripMargin

    private val AccessDenied =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<Error>
          |  <Code>AccessDenied</Code>
          |  <Message>Access Denied</Message>
          |  <RequestId>req-1</RequestId>
          |</Error>""".stripMargin

    private def bytesOf(s: String): Span[Byte] =
        Span.fromUnsafe(s.getBytes("UTF-8"))

    "NoSuchKey -> S3NotFoundException" in run {
        val bucket = S3Bucket.unsafe("my-bucket")
        ErrorParser
            .toException(HttpStatus.NotFound, "GET", "/my-bucket/missing.txt", bucket.value, Present("missing.txt"), bytesOf(NoSuchKey))
            .map { e =>
                assert(e.isInstanceOf[S3NotFoundException])
                val nf = e.asInstanceOf[S3NotFoundException]
                assert(nf.code == "NoSuchKey")
                assert(nf.requestId == "0A49CE4060975EAC")
            }
    }

    "AccessDenied -> S3AuthException" in run {
        ErrorParser
            .toException(HttpStatus.Forbidden, "GET", "/my-bucket/foo", "my-bucket", Present("foo"), bytesOf(AccessDenied))
            .map { e =>
                assert(e.isInstanceOf[S3AuthException])
                assert(e.asInstanceOf[S3AuthException].code == "AccessDenied")
            }
    }

    "malformed XML still surfaces a typed S3 error" in run {
        ErrorParser
            .toException(HttpStatus.InternalServerError, "GET", "/x", "x", Absent, bytesOf("not xml"))
            .map { e =>
                // Falls back to S3ApiException with synthetic code
                assert(e.isInstanceOf[S3ApiException])
                assert(e.asInstanceOf[S3ApiException].status.code == 500)
            }
    }

end ErrorParserTest

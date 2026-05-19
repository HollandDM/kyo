package kyo

import kyo.*

/** AWS credentials used to sign S3 requests.
  *
  * v1 supports two shapes: [[S3Credentials.Static]] (long-term or session credentials passed explicitly) and [[S3Credentials.Anonymous]]
  * (no signing — used for public buckets and tests). The environment chain is exposed via [[S3Credentials.fromEnv]] as an effectful loader
  * rather than a [[S3Credentials]] case, because reading environment variables is a side effect that should be visible in the type signature.
  */
sealed trait S3Credentials

object S3Credentials:

    /** Static AWS credentials. `sessionToken` is provided when using temporary credentials from STS or instance profiles. */
    case class Static(
        accessKeyId: String,
        secretAccessKey: String,
        sessionToken: Maybe[String] = Absent
    ) extends S3Credentials

    /** No signing. Requests go out unsigned; suitable for public buckets and tests against MinIO without auth. */
    case object Anonymous extends S3Credentials

    /** Reads credentials from environment variables `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, and optionally `AWS_SESSION_TOKEN`.
      *
      * Aborts with [[S3AuthException]] when the key or secret is missing. Future versions may extend this to read the AWS credentials file
      * and IMDS; this is intentionally minimal for v1.
      */
    def fromEnv(using Frame): S3Credentials < (Sync & Abort[S3AuthException]) =
        Sync.defer {
            val key    = sys.env.get("AWS_ACCESS_KEY_ID")
            val secret = sys.env.get("AWS_SECRET_ACCESS_KEY")
            val token  = sys.env.get("AWS_SESSION_TOKEN")
            (key, secret) match
                case (Some(k), Some(s)) =>
                    Static(k, s, Maybe.fromOption(token)): S3Credentials
                case _ =>
                    Abort.fail(S3AuthException(
                        code = "MissingCredentials",
                        awsMessage = "AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY must be set",
                        requestId = ""
                    ))
        }

end S3Credentials

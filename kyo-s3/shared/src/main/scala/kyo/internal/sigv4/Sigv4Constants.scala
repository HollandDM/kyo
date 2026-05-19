package kyo.internal.sigv4

/** Constants for AWS Signature Version 4 signing. Kept separate from [[Signer]] so they can be inlined and audited in isolation. */
private[kyo] object Sigv4Constants:

    val Algorithm: String      = "AWS4-HMAC-SHA256"
    val Terminator: String     = "aws4_request"
    val SecretPrefix: String   = "AWS4"
    val Service: String        = "s3"
    val UnsignedPayload: String = "UNSIGNED-PAYLOAD"

    /** SHA-256 hex of the empty string. Used as the payload hash for GET/DELETE/HEAD and empty PUT bodies. */
    val EmptyBodySha256Hex: String =
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

end Sigv4Constants

package kyo

import kyo.*

/** A validated S3 bucket name.
  *
  * Built via [[S3Bucket.apply]], which enforces the subset of AWS bucket-naming rules common to virtual-host addressing: length 3–63,
  * lowercase letters/digits/hyphens, must start and end with a letter or digit, no consecutive dots, not formatted as an IP address.
  *
  * Use [[S3Bucket.unsafe]] only when the value is known to be valid (e.g., loaded from trusted configuration).
  */
opaque type S3Bucket = String

object S3Bucket:

    /** Validates and constructs a bucket name. Aborts with [[S3ValidationException]] on invalid input. */
    def apply(name: String)(using Frame): S3Bucket < Abort[S3ValidationException] =
        validate(name) match
            case Present(err) => Abort.fail(S3ValidationException("bucket", err))
            case Absent       => (name: S3Bucket)

    /** Skip validation. Use only for values from trusted sources. */
    def unsafe(name: String): S3Bucket = name

    extension (self: S3Bucket)
        def value: String = self

    private def validate(name: String): Maybe[String] =
        if name.length < 3 || name.length > 63 then
            Present(s"length must be 3..63 (got ${name.length})")
        else if !isValidChars(name) then
            Present("must contain only lowercase letters, digits, hyphens, or dots")
        else if !isEdgeAlnum(name.charAt(0)) || !isEdgeAlnum(name.charAt(name.length - 1)) then
            Present("must start and end with a lowercase letter or digit")
        else if name.contains("..") then
            Present("must not contain consecutive dots")
        else if looksLikeIp(name) then
            Present("must not be formatted as an IPv4 address")
        else Absent

    private def isValidChars(s: String): Boolean =
        var i = 0
        while i < s.length do
            val c = s.charAt(i)
            val ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '.'
            if !ok then return false
            i += 1
        true

    private def isEdgeAlnum(c: Char): Boolean =
        (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')

    private def looksLikeIp(s: String): Boolean =
        val parts = s.split('.')
        parts.length == 4 && parts.forall { p =>
            p.nonEmpty && p.length <= 3 && p.forall(c => c >= '0' && c <= '9') &&
            p.toIntOption.exists(n => n >= 0 && n <= 255)
        }

end S3Bucket

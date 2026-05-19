package kyo

/** An AWS region identifier (e.g. `"us-east-1"`).
  *
  * S3 SigV4 signing requires the region as part of the credential scope, so the region is a first-class type rather than a string. The
  * companion exposes constants for the regions used most often; use [[S3Region.unsafe]] for arbitrary values such as those used by
  * S3-compatible services.
  */
opaque type S3Region = String

object S3Region:

    /** Construct a region from a raw identifier. No validation is performed; AWS region naming evolves and S3-compatible services accept
      * arbitrary strings (for example, MinIO defaults to `"us-east-1"` regardless of physical location).
      */
    def unsafe(name: String): S3Region = name

    extension (self: S3Region) def value: String = self

    val usEast1:      S3Region = "us-east-1"
    val usEast2:      S3Region = "us-east-2"
    val usWest1:      S3Region = "us-west-1"
    val usWest2:      S3Region = "us-west-2"
    val euCentral1:   S3Region = "eu-central-1"
    val euWest1:      S3Region = "eu-west-1"
    val euWest2:      S3Region = "eu-west-2"
    val euNorth1:     S3Region = "eu-north-1"
    val apSoutheast1: S3Region = "ap-southeast-1"
    val apSoutheast2: S3Region = "ap-southeast-2"
    val apNortheast1: S3Region = "ap-northeast-1"
    val apSouth1:     S3Region = "ap-south-1"

end S3Region

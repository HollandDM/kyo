package kyo

import kyo.*

/** Result of a successful `S3.putObject` or `S3.putObjectStream`.
  *
  * `etag` is the entity tag S3 assigned to the new object (typically the MD5 hash, in quotes). `versionId` is present when the bucket has
  * versioning enabled.
  */
case class S3PutResult(
    etag: String,
    versionId: Maybe[String]
) derives CanEqual

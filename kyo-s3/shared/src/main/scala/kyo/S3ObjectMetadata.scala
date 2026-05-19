package kyo

import kyo.*

/** Metadata for a single S3 object, parsed from response headers (typically returned by `S3.headObject`).
  *
  * `userMetadata` carries the `x-amz-meta-*` headers with the prefix stripped and names lowercased (as S3 stores them).
  */
case class S3ObjectMetadata(
    contentLength: Long,
    contentType: Maybe[String],
    etag: String,
    lastModified: Instant,
    versionId: Maybe[String],
    userMetadata: Map[String, String]
) derives CanEqual

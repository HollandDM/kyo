package kyo

import kyo.*

/** Summary information for an object as returned by `S3.listObjects`.
  *
  * `lastModified` is the object's last-modified instant in UTC. `etag` is the object's entity tag (typically the MD5 of the object, wrapped
  * in quotes). `storageClass` is absent for `STANDARD` and `Present(...)` for `STANDARD_IA`, `GLACIER`, etc.
  */
case class S3Object(
    key: String,
    size: Long,
    etag: String,
    lastModified: Instant,
    storageClass: Maybe[String]
) derives CanEqual

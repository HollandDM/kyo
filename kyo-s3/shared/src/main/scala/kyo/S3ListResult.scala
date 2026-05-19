package kyo

import kyo.*

/** Page of results from a `ListObjectsV2` call.
  *
  *   - `objects` holds the objects under the requested prefix.
  *   - `commonPrefixes` is populated only when a delimiter is supplied: it contains directory-like prefixes that share the delimiter.
  *   - `isTruncated` is true when more results are available beyond this page.
  *   - `nextContinuationToken` (present when `isTruncated`) should be passed as `continuationToken` to fetch the next page.
  *   - `keyCount` is the number of keys returned in this page (including any common prefixes).
  */
case class S3ListResult(
    objects: Chunk[S3Object],
    commonPrefixes: Chunk[String],
    isTruncated: Boolean,
    continuationToken: Maybe[String],
    nextContinuationToken: Maybe[String],
    keyCount: Int
) derives CanEqual

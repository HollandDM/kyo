package kyo

/** Shared base for kyo-s3 tests. Inherits the kyo-http base so the HttpClient default config (60s timeout) is set for any tests that hit
  * the network; pure unit tests just ignore that.
  */
abstract class S3Test extends Test
